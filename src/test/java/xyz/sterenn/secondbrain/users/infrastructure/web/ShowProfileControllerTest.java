package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Les quatre issues du profil, plus le parcours complet. Les jetons ne sont pas simulés :
 * ils sont émis par le port réel, ou obtenus par la vraie route de connexion.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShowProfileControllerTest {

    private static final String MOT_DE_PASSE = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void vide_les_notifications_enregistrees() {
        recordingNotificationSender.clear();
    }

    @Test
    void refuse_l_acces_sans_jeton() throws Exception {
        mockMvc.perform(get("/api/profile"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_un_jeton_expire() throws Exception {
        // Tolérance d'horloge du décodeur : 60 s. On date donc deux heures en arrière.
        Instant ilYaDeuxHeures = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet revendications = JwtClaimsSet.builder()
            .subject(UUID.randomUUID().toString())
            .issuedAt(ilYaDeuxHeures)
            .expiresAt(ilYaDeuxHeures.plus(Duration.ofMinutes(1)))
            .build();
        String jetonExpire = jwtEncoder.encode(JwtEncoderParameters.from(revendications)).getTokenValue();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonExpire))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refuse_un_jeton_bien_signe_dont_le_compte_n_existe_pas() throws Exception {
        Instant maintenant = Instant.now();
        String jeton = accessTokenIssuer
            .issue(UUID.randomUUID(), maintenant, maintenant.plus(Duration.ofHours(1)))
            .value();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rend_le_profil_du_porteur_du_jeton() throws Exception {
        UUID compte = AccountFixture.registerVerified(
            commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);
        Instant maintenant = Instant.now();
        String jeton = accessTokenIssuer
            .issue(compte, maintenant, maintenant.plus(Duration.ofHours(1)))
            .value();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("alice@exemple.fr"))
            .andExpect(jsonPath("$.verified").value(true))
            .andExpect(jsonPath("$.id").value(compte.toString()));
    }

    @Test
    void bout_en_bout_de_la_connexion_au_profil() throws Exception {
        AccountFixture.registerVerified(
            commandBus, recordingNotificationSender, "alice@exemple.fr", MOT_DE_PASSE);

        String corps = mockMvc.perform(post("/api/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "password")
                .param("username", "alice@exemple.fr")
                .param("password", MOT_DE_PASSE))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        String jeton = JsonPath.read(corps, "$.access_token");

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("alice@exemple.fr"));
    }
}
