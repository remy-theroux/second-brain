package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;

class EmbeddingTest {

    @Test
    void accepte_un_vecteur_de_la_dimension_attendue() {
        Embedding vecteur = Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS));

        assertThat(vecteur.values()).hasSize(EmbeddingPolicy.DIMENSIONS);
    }

    @Test
    void refuse_un_vecteur_trop_court_en_nommant_la_dimension_recue() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Embedding.of(unVecteur(768)))
                .withMessageContaining("768")
                .withMessageContaining(String.valueOf(EmbeddingPolicy.DIMENSIONS));
    }

    @Test
    void refuse_un_vecteur_absent() {
        assertThatNullPointerException().isThrownBy(() -> Embedding.of(null));
    }

    @Test
    void deux_vecteurs_de_meme_contenu_sont_egaux() {
        assertThat(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)))
                .isEqualTo(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)))
                .hasSameHashCodeAs(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)));
    }

    @Test
    void ne_laisse_pas_modifier_le_tableau_qu_il_a_recu() {
        float[] source = unVecteur(EmbeddingPolicy.DIMENSIONS);
        Embedding vecteur = Embedding.of(source);

        source[0] = 42f;

        assertThat(vecteur.values()[0]).isEqualTo(0.5f);
    }

    @Test
    void ne_laisse_pas_modifier_le_tableau_qu_il_rend() {
        Embedding vecteur = Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS));

        vecteur.values()[0] = 42f;

        assertThat(vecteur.values()[0]).isEqualTo(0.5f);
    }

    @Test
    void ne_montre_jamais_ses_valeurs_quand_on_l_affiche() {
        assertThat(Embedding.of(unVecteur(EmbeddingPolicy.DIMENSIONS)).toString())
                .contains(String.valueOf(EmbeddingPolicy.DIMENSIONS))
                .doesNotContain("0.5");
    }

    /** Un vecteur constant : ce qui est testé ici, c'est la forme, jamais le contenu. */
    private static float[] unVecteur(int dimensions) {
        float[] valeurs = new float[dimensions];
        Arrays.fill(valeurs, 0.5f);
        return valeurs;
    }
}
