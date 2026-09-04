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

    private static final String MOT_DE_PASSE_VALIDE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RecordingNotificationSender notifications;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void vide_les_notifications() {
        notifications.clear();
    }

    private VerificationNotification inscrit(String email) throws Exception {
        mockMvc.perform(post("/api/registrations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                {"email": "%s", "password": "%s"}
                """.formatted(email, MOT_DE_PASSE_VALIDE)));
        return notifications.derniere();
    }

    @Test
    void verifie_le_compte_et_redirige_vers_la_connexion_quand_je_suis_le_lien_recu() throws Exception {
        VerificationNotification notification = inscrit("alice@example.com");

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
    void refuse_un_lien_falsifie() throws Exception {
        VerificationNotification notification = inscrit("bob@example.com");

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
    void refuse_un_lien_dont_le_compte_est_inconnu_avec_le_meme_code_qu_un_lien_falsifie() throws Exception {
        VerificationNotification notification = inscrit("carol@example.com");

        mockMvc.perform(get("/verification")
                        .param("compte", UUID.randomUUID().toString())
                        .param("jeton", notification.rawToken().value()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=lien-invalide"));
    }

    @Test
    void refuse_un_lien_deja_utilise_et_le_dit() throws Exception {
        VerificationNotification notification = inscrit("dave@example.com");
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
    void refuse_un_lien_sans_parametre() throws Exception {
        mockMvc.perform(get("/verification"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?verification=lien-invalide"));
    }
}
