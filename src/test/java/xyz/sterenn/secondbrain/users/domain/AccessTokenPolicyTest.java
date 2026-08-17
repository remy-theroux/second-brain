package xyz.sterenn.secondbrain.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessTokenPolicyTest {

    @Test
    void fait_expirer_le_jeton_une_heure_apres_son_emission() {
        Instant emission = Instant.parse("2026-08-17T10:00:00Z");

        assertThat(AccessTokenPolicy.expiresAt(emission))
            .isEqualTo(Instant.parse("2026-08-17T11:00:00Z"));
    }

    @Test
    void annonce_une_duree_de_vie_d_une_heure() {
        assertThat(AccessTokenPolicy.LIFETIME).isEqualTo(Duration.ofHours(1));
    }
}
