package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessTokenTest {

    private static final Instant MAINTENANT = Instant.parse("2026-08-17T10:00:00Z");

    @Test
    void refuse_une_valeur_vide() {
        assertThatThrownBy(() -> new AccessToken("  ", MAINTENANT))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_une_expiration_absente() {
        assertThatThrownBy(() -> new AccessToken("eyJ", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void compte_les_secondes_restantes() {
        AccessToken jeton = new AccessToken("eyJ", MAINTENANT.plusSeconds(3600));

        assertThat(jeton.expiresIn(MAINTENANT)).isEqualTo(3600L);
    }

    @Test
    void ne_compte_jamais_de_secondes_negatives() {
        AccessToken jeton = new AccessToken("eyJ", MAINTENANT.minusSeconds(10));

        assertThat(jeton.expiresIn(MAINTENANT)).isZero();
    }

    @Test
    void ne_divulgue_pas_sa_valeur_dans_son_rendu_texte() {
        AccessToken jeton = new AccessToken("eyJ.secret.abc", MAINTENANT);

        assertThat(jeton.toString()).doesNotContain("eyJ.secret.abc");
    }
}
