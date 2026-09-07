package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ShowProfileControllerTest {

    private static final String PASSWORD = "chevalpile42";

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
    void clears_the_recorded_notifications() {
        recordingNotificationSender.clear();
    }

    @Test
    void rejects_access_without_a_token() throws Exception {
        mockMvc.perform(get("/api/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejects_an_expired_token() throws Exception {
        // Decoder clock skew tolerance: 60 s. So date it two hours back.
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(twoHoursAgo)
                .expiresAt(twoHoursAgo.plus(Duration.ofMinutes(1)))
                .build();
        String expiredToken =
                jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejects_a_token_signed_with_another_key() throws Exception {
        // Claims deliberately valid in every respect: only the signature must explain the
        // refusal.
        JwtEncoder foreignEncoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(
                        "une-autre-cle-de-signature-32-octets-au-moins".getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        String forgedToken =
                foreignEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejects_a_well_signed_token_whose_account_does_not_exist() throws Exception {
        Instant now = Instant.now();
        String token = accessTokenIssuer
                .issue(UUID.randomUUID(), now, now.plus(Duration.ofHours(1)))
                .value();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_the_profile_of_the_token_bearer() throws Exception {
        UUID accountId =
                AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        Instant now = Instant.now();
        String token = accessTokenIssuer
                .issue(accountId, now, now.plus(Duration.ofHours(1)))
                .value();

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@exemple.fr"))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.id").value(accountId.toString()));
    }

    @Test
    void end_to_end_from_the_login_to_the_profile() throws Exception {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        String body = mockMvc.perform(post("/api/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("username", "alice@exemple.fr")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = JsonPath.read(body, "$.access_token");

        mockMvc.perform(get("/api/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("alice@exemple.fr"));
    }
}
