package xyz.sterenn.secondbrain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JwtConfigurationTest {

    private static final String SUBJECT = "7f000001-0000-4000-8000-000000000000";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void reads_back_a_token_it_has_just_signed() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(SUBJECT)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        Jwt decoded = jwtDecoder.decode(value);
        assertThat(decoded.getSubject()).isEqualTo(SUBJECT);
        assertThat(decoded.getExpiresAt()).isNotNull();
    }

    @Test
    void rejects_an_expired_token() {
        // Two hours, not one minute: the decoder tolerates 60 seconds of clock skew.
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(SUBJECT)
                .issuedAt(twoHoursAgo)
                .expiresAt(twoHoursAgo.plus(Duration.ofMinutes(1)))
                .build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(value)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejects_a_too_short_secret_at_startup() {
        assertThatThrownBy(() -> new JwtConfiguration("trop-court"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
