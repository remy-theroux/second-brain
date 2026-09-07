package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
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
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssueAccessTokenControllerTest {

    private static final String PASSWORD = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @BeforeEach
    void clears_the_recorded_notifications() {
        recordingNotificationSender.clear();
    }

    @Test
    void issues_a_token_to_a_verified_account() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    void forbids_caching_the_response() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    void rejects_an_incorrect_password_as_invalid_grant() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice@exemple.fr")
                        .param("password", "chevalpile43"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description").value("Email ou mot de passe incorrect."));
    }

    @Test
    void rejects_an_unknown_email_as_invalid_grant() throws Exception {
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "inconnu@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void explains_that_an_account_is_not_verified() throws Exception {
        AccountFixture.register(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "bob@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description", Matchers.containsString("n'est pas encore vérifié")));
    }

    @Test
    void rejects_an_unknown_grant_type() throws Exception {
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("username", "alice@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    @Test
    void rejects_a_request_without_a_grant_type() throws Exception {
        // defaultValue = "" on the controller side: our own error comes out, not Spring's
        // generic 400 on a missing parameter.
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void rejects_a_request_without_credentials() throws Exception {
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }
}
