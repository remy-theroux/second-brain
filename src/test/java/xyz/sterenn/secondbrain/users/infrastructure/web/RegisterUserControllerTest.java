package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.application.query.FindUserByEmail;
import xyz.sterenn.secondbrain.users.application.query.UserView;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RegisterUserControllerTest {

    private static final String VALID_PASSWORD = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueryBus queryBus;

    private static String body(String email, String password) {
        return """
            {"email": "%s", "password": "%s"}
            """.formatted(email, password);
    }

    @Test
    void creates_the_account_and_answers_201_on_success() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice@example.com", VALID_PASSWORD)))
                .andExpect(status().isCreated());

        Optional<UserView> view = queryBus.ask(new FindUserByEmail("alice@example.com"));
        assertThat(view).isPresent();
        assertThat(view.get().verified()).isFalse();
    }

    @Test
    void rejects_an_already_used_email_with_an_error_on_the_email_field() throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("bob@example.com", VALID_PASSWORD)));

        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob@example.com", VALID_PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").doesNotExist());
    }

    @Test
    void rejects_a_weak_password_with_an_error_on_the_password_field() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("carol@example.com", "court")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.email").doesNotExist());

        assertThat(queryBus.ask(new FindUserByEmail("carol@example.com"))).isEmpty();
    }

    @Test
    void rejects_a_malformed_email_with_an_error_on_the_email_field() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("pas-un-email", VALID_PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    void rejects_blank_fields_naming_both() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // OVERRIDE isolates the context: two @Primary NotificationSender beans in the same
    // context would conflict. And no @Transactional, because the bus rollback nested in a
    // test transaction would only be marked, not executed — hence the @AfterEach.
    @Nested
    @NestedTestConfiguration(EnclosingConfiguration.OVERRIDE)
    @Import({TestcontainersConfiguration.class, WhenSendingFails.SendingFailureConfiguration.class})
    @SpringBootTest
    @AutoConfigureMockMvc
    class WhenSendingFails {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private QueryBus queryBus;

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @AfterEach
        void cleanUp() {
            jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", "erin@example.com");
        }

        @Test
        void answers_503_without_a_field_error_and_rolls_back_the_account_creation() throws Exception {
            mockMvc.perform(post("/api/registrations")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("erin@example.com", VALID_PASSWORD)))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.errors").doesNotExist());

            assertThat(queryBus.ask(new FindUserByEmail("erin@example.com"))).isEmpty();
        }

        @TestConfiguration(proxyBeanMethods = false)
        static class SendingFailureConfiguration {

            @Bean
            @Primary
            NotificationSender notificationSender() {
                return notification -> {
                    throw new MailSendException("échec d'envoi simulé pour le test");
                };
            }
        }
    }
}
