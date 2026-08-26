package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

class ExtractedTextTest {

    private static final String ASSEZ_LONG = "Un texte assez long pour franchir le plancher des cinquante.";

    @Test
    void garde_ses_blocs_dans_l_ordre_ou_ils_arrivent() {
        TextBlock premier = TextBlock.of("Un", 1, ASSEZ_LONG);
        TextBlock second = TextBlock.of("Deux", 1, ASSEZ_LONG);

        assertThat(new ExtractedText(List.of(premier, second)).blocks()).containsExactly(premier, second);
    }

    @Test
    void refuse_un_document_sans_aucun_bloc() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> new ExtractedText(List.of()))
                .withMessageContaining("pas de texte exploitable");
    }

    @Test
    void refuse_un_document_sous_le_plancher_de_caracteres() {
        String troisBribes = "3 Page 1";
        assertThat(troisBribes.length()).isLessThan(ExtractionPolicy.MINIMUM_USEFUL_CHARACTERS);

        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> ExtractedText.untitled(troisBribes));
    }

    @Test
    void additionne_les_caracteres_de_tous_ses_blocs_sans_compter_les_titres() {
        ExtractedText texte = new ExtractedText(
                List.of(TextBlock.of("Un titre qui ne compte pas", 1, ASSEZ_LONG), TextBlock.untitled(ASSEZ_LONG)));

        assertThat(texte.characterCount()).isEqualTo(ASSEZ_LONG.length() * 2);
    }

    @Test
    void ne_se_laisse_pas_modifier_par_la_liste_qu_on_lui_a_donnee() {
        List<TextBlock> mutable = new ArrayList<>(List.of(TextBlock.untitled(ASSEZ_LONG)));
        ExtractedText texte = new ExtractedText(mutable);

        mutable.clear();

        assertThat(texte.blocks()).hasSize(1);
    }
}
