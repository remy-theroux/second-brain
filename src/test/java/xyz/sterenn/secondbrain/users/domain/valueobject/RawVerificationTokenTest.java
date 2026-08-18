package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Value object sans dépendance : test unitaire pur, sans Spring.
 */
class RawVerificationTokenTest {

    @Test
    void genere_un_jeton_non_vide() {
        assertThat(RawVerificationToken.generate().value()).isNotBlank();
    }

    @Test
    void genere_un_jeton_utilisable_tel_quel_dans_une_url() {
        // base64url sans padding : lettres, chiffres, tiret et souligné uniquement.
        assertThat(RawVerificationToken.generate().value()).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void genere_un_jeton_assez_long_pour_ne_pas_etre_devine() {
        // 32 octets encodés en base64 sans padding donnent 43 caractères.
        assertThat(RawVerificationToken.generate().value()).hasSize(43);
    }

    @Test
    void genere_un_jeton_different_a_chaque_appel() {
        Set<String> jetons = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            jetons.add(RawVerificationToken.generate().value());
        }
        assertThat(jetons).hasSize(100);
    }

    @Test
    void refuse_un_jeton_vide() {
        assertThatThrownBy(() -> new RawVerificationToken("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ne_divulgue_pas_le_jeton_dans_sa_representation_textuelle() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        assertThat(jeton.toString()).doesNotContain(jeton.value());
    }
}
