package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;

/**
 * L'adapter n'a besoin d'aucun contexte Spring : il s'instancie directement.
 */
class BCryptTokenHasherTest {

    private final TokenHasher hasher = new BCryptTokenHasher();

    @Test
    void ne_rend_jamais_le_jeton_en_clair() {
        String jeton = RawVerificationToken.generate().value();

        assertThat(hasher.hash(jeton)).doesNotContain(jeton);
    }

    @Test
    void reconnait_le_jeton_d_origine() {
        String jeton = RawVerificationToken.generate().value();

        assertThat(hasher.matches(jeton, hasher.hash(jeton))).isTrue();
    }

    @Test
    void refuse_un_autre_jeton() {
        String empreinte = hasher.hash(RawVerificationToken.generate().value());

        assertThat(hasher.matches(RawVerificationToken.generate().value(), empreinte)).isFalse();
    }

    @Test
    void produit_une_empreinte_differente_a_chaque_appel_pour_un_meme_jeton() {
        // Le salt est tiré au hasard et embarqué dans l'empreinte : deux hachages du
        // même jeton diffèrent, et aucune table précalculée n'est exploitable.
        String jeton = RawVerificationToken.generate().value();

        assertThat(hasher.hash(jeton)).isNotEqualTo(hasher.hash(jeton));
    }
}
