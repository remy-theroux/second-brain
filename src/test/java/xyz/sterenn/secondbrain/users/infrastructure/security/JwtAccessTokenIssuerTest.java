package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JwtAccessTokenIssuerTest {

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void issues_a_decodable_token_carrying_the_account_as_subject() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();

        AccessToken token = accessTokenIssuer.issue(accountId, now, now.plusSeconds(3600));

        Jwt decoded = jwtDecoder.decode(token.value());
        assertThat(decoded.getSubject()).isEqualTo(accountId.toString());
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isNotNull();
    }

    @Test
    void carries_the_requested_expiration_over_to_the_token_and_to_the_value() {
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(1800);

        AccessToken token = accessTokenIssuer.issue(accountId, now, expiresAt);

        assertThat(token.expiresAt()).isEqualTo(expiresAt);
        // JWT claims are dated to the second: compare at the same granularity.
        assertThat(jwtDecoder.decode(token.value()).getExpiresAt().getEpochSecond())
                .isEqualTo(expiresAt.getEpochSecond());
    }

    @Test
    void writes_no_claim_about_the_email() {
        AccessToken token = accessTokenIssuer.issue(
                UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600));

        assertThat(jwtDecoder.decode(token.value()).getClaims()).doesNotContainKey("email");
    }
}
