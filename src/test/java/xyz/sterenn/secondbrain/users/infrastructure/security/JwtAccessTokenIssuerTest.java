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

/**
 * C'est le <em>port</em> qui est injecté, pas l'adapter : ce qui est vérifié, c'est le
 * contrat du domaine. Le décodeur, lui, sert de témoin indépendant — il prouve que ce qui
 * a été émis est bien un JWT valide portant les revendications attendues.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JwtAccessTokenIssuerTest {

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void emet_un_jeton_decodable_portant_le_compte_en_sujet() {
        UUID compte = UUID.randomUUID();
        Instant maintenant = Instant.now();

        AccessToken jeton = accessTokenIssuer.issue(compte, maintenant, maintenant.plusSeconds(3600));

        Jwt decode = jwtDecoder.decode(jeton.value());
        assertThat(decode.getSubject()).isEqualTo(compte.toString());
        assertThat(decode.getIssuedAt()).isNotNull();
        assertThat(decode.getExpiresAt()).isNotNull();
    }

    @Test
    void reporte_l_expiration_demandee_sur_le_jeton_et_sur_la_valeur() {
        UUID compte = UUID.randomUUID();
        Instant maintenant = Instant.now();
        Instant expiration = maintenant.plusSeconds(1800);

        AccessToken jeton = accessTokenIssuer.issue(compte, maintenant, expiration);

        assertThat(jeton.expiresAt()).isEqualTo(expiration);
        // Les revendications JWT sont datées à la seconde : on compare au même grain.
        assertThat(jwtDecoder.decode(jeton.value()).getExpiresAt().getEpochSecond())
                .isEqualTo(expiration.getEpochSecond());
    }

    @Test
    void n_ecrit_aucune_revendication_sur_l_email() {
        // Décision 3 du design : le jeton ne transporte pas de donnée personnelle.
        AccessToken jeton = accessTokenIssuer.issue(
                UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(3600));

        assertThat(jwtDecoder.decode(jeton.value()).getClaims()).doesNotContainKey("email");
    }
}
