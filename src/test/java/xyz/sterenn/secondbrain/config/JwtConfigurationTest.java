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

    private static final String SUJET = "7f000001-0000-4000-8000-000000000000";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void relit_un_jeton_qu_il_vient_de_signer() {
        Instant maintenant = Instant.now();
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .subject(SUJET)
                .issuedAt(maintenant)
                .expiresAt(maintenant.plus(Duration.ofHours(1)))
                .build();

        String valeur =
                jwtEncoder.encode(JwtEncoderParameters.from(revendications)).getTokenValue();

        Jwt relu = jwtDecoder.decode(valeur);
        assertThat(relu.getSubject()).isEqualTo(SUJET);
        assertThat(relu.getExpiresAt()).isNotNull();
    }

    @Test
    void refuse_un_jeton_expire() {
        // Deux heures, pas une minute : le décodeur tolère 60 secondes de dérive d'horloge.
        Instant ilYaDeuxHeures = Instant.now().minus(Duration.ofHours(2));
        JwtClaimsSet revendications = JwtClaimsSet.builder()
                .subject(SUJET)
                .issuedAt(ilYaDeuxHeures)
                .expiresAt(ilYaDeuxHeures.plus(Duration.ofMinutes(1)))
                .build();
        String valeur =
                jwtEncoder.encode(JwtEncoderParameters.from(revendications)).getTokenValue();

        assertThatThrownBy(() -> jwtDecoder.decode(valeur)).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void refuse_un_secret_trop_court_au_demarrage() {
        assertThatThrownBy(() -> new JwtConfiguration("trop-court"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 octets");
    }
}
