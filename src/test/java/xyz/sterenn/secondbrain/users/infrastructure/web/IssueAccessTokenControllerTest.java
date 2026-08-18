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

/**
 * La forme du protocole est ce qui est vérifié ici : noms des paramètres, noms des champs
 * de la réponse, codes d'erreur RFC 6749. Le front en dépend au caractère près.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IssueAccessTokenControllerTest {

    private static final String MOT_DE_PASSE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @BeforeEach
    void vide_les_notifications_enregistrees() {
        recordingNotificationSender.clear();
    }

    @Test
    void delivre_un_jeton_a_un_compte_verifie() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice@exemple.fr")
                        .param("password", MOT_DE_PASSE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600));
    }

    @Test
    void interdit_la_mise_en_cache_de_la_reponse() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice@exemple.fr")
                        .param("password", MOT_DE_PASSE))
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-store")));
    }

    @Test
    void refuse_un_mot_de_passe_incorrect_en_invalid_grant() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

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
    void refuse_un_email_inconnu_en_invalid_grant() throws Exception {
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "inconnu@exemple.fr")
                        .param("password", MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void explique_qu_un_compte_n_est_pas_verifie() throws Exception {
        AccountFixture.register(commandBus, recordingNotificationSender, "bob@exemple.fr", MOT_DE_PASSE);

        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "bob@exemple.fr")
                        .param("password", MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.error_description", Matchers.containsString("n'est pas encore vérifié")));
    }

    @Test
    void refuse_un_type_d_autorisation_inconnu() throws Exception {
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("username", "alice@exemple.fr")
                        .param("password", MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
    }

    @Test
    void refuse_une_requete_sans_type_d_autorisation() throws Exception {
        // defaultValue = "" côté contrôleur : c'est notre erreur qui sort, pas le 400
        // générique de Spring sur paramètre manquant.
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "alice@exemple.fr")
                        .param("password", MOT_DE_PASSE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void refuse_une_requete_sans_identifiants() throws Exception {
        mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }
}
