package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VerifyAccountControllerTest {

    private static final String VALID_PASSWORD = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingNotificationSender notifications;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void clears_the_notifications() {
        notifications.clear();
    }

    private VerificationNotification register(String email) throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, VALID_PASSWORD)));
        return notifications.last();
    }

    @Test
    void verifies_the_account_and_redirects_to_the_login_when_the_received_link_is_followed() throws Exception {
        VerificationNotification notification = register("alice@example.com");

        mockMvc.perform(get("/verification")
                        .param("compte", notification.accountId().toString())
                        .param("jeton", notification.rawToken().value()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=ok"));

        assertThat(userRepository
                        .findById(notification.accountId())
                        .orElseThrow()
                        .isVerified())
                .isTrue();
    }

    @Test
    void rejects_a_forged_link() throws Exception {
        VerificationNotification notification = register("bob@example.com");

        mockMvc.perform(get("/verification")
                        .param("compte", notification.accountId().toString())
                        .param("jeton", "un-autre-jeton"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=lien-invalide"));

        assertThat(userRepository
                        .findById(notification.accountId())
                        .orElseThrow()
                        .isVerified())
                .isFalse();
    }

    @Test
    void rejects_a_link_whose_account_is_unknown_with_the_same_code_as_a_forged_link() throws Exception {
        VerificationNotification notification = register("carol@example.com");

        mockMvc.perform(get("/verification")
                        .param("compte", UUID.randomUUID().toString())
                        .param("jeton", notification.rawToken().value()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=lien-invalide"));
    }

    @Test
    void rejects_an_already_used_link_and_says_so() throws Exception {
        VerificationNotification notification = register("dave@example.com");
        mockMvc.perform(get("/verification")
                .param("compte", notification.accountId().toString())
                .param("jeton", notification.rawToken().value()));

        mockMvc.perform(get("/verification")
                        .param("compte", notification.accountId().toString())
                        .param("jeton", notification.rawToken().value()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=lien-deja-utilise"));
    }

    @Test
    void rejects_a_link_without_parameters() throws Exception {
        mockMvc.perform(get("/verification"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=lien-invalide"));
    }
}
