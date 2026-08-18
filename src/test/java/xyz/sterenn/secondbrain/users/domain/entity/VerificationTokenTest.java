package xyz.sterenn.secondbrain.users.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;

/**
 * Agrégat sans dépendance : test unitaire pur. Le temps entre par paramètre, ce qui
 * permet de vérifier l'expiration sans attendre 24 heures.
 */
class VerificationTokenTest {

    private static final Instant EMISSION = Instant.parse("2026-08-06T10:00:00Z");
    private static final UUID COMPTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private VerificationToken emis() {
        return VerificationToken.issue(COMPTE, "empreinte", EMISSION);
    }

    @Test
    void nait_valide_et_non_consomme() {
        VerificationToken jeton = emis();

        assertThat(jeton.getUserId()).isEqualTo(COMPTE);
        assertThat(jeton.getTokenHash()).isEqualTo("empreinte");
        assertThat(jeton.isConsumed()).isFalse();
        assertThat(jeton.isExpired(EMISSION)).isFalse();
    }

    @Test
    void expire_vingt_quatre_heures_apres_son_emission() {
        VerificationToken jeton = emis();

        assertThat(jeton.getExpiresAt()).isEqualTo(EMISSION.plus(VerificationToken.VALIDITY));
        assertThat(jeton.isExpired(EMISSION.plus(Duration.ofHours(23)))).isFalse();
        assertThat(jeton.isExpired(EMISSION.plus(Duration.ofHours(25)))).isTrue();
    }

    @Test
    void marque_l_instant_de_consommation() {
        VerificationToken jeton = emis();
        Instant clic = EMISSION.plus(Duration.ofMinutes(5));

        jeton.consume(clic);

        assertThat(jeton.isConsumed()).isTrue();
        assertThat(jeton.getConsumedAt()).isEqualTo(clic);
    }

    @Test
    void refuse_une_seconde_consommation() {
        VerificationToken jeton = emis();
        jeton.consume(EMISSION.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> jeton.consume(EMISSION.plus(Duration.ofMinutes(10))))
                .isInstanceOf(AlreadyUsedVerificationLinkException.class);
    }

    @Test
    void refuse_la_consommation_d_un_jeton_expire() {
        VerificationToken jeton = emis();

        assertThatThrownBy(() -> jeton.consume(EMISSION.plus(Duration.ofHours(25))))
                .isInstanceOf(ExpiredVerificationLinkException.class);
        assertThat(jeton.isConsumed()).isFalse();
    }

    @Test
    void signale_d_abord_le_double_usage_quand_le_jeton_est_aussi_expire() {
        // L'utilisateur qui reclique un vieux lien déjà utilisé a besoin de savoir
        // qu'il a déjà vérifié son compte, pas que le lien a vieilli.
        VerificationToken jeton = emis();
        jeton.consume(EMISSION.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> jeton.consume(EMISSION.plus(Duration.ofHours(25))))
                .isInstanceOf(AlreadyUsedVerificationLinkException.class);
    }
}
