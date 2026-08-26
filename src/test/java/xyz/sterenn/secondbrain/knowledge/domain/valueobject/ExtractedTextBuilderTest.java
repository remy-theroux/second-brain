package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

class ExtractedTextBuilderTest {

    private static final String ASSEZ_LONG = "Un texte assez long pour franchir le plancher des cinquante.";

    @Test
    void assemble_les_sections_dans_l_ordre() {
        ExtractedText texte = new ExtractedTextBuilder()
                .section("Introduction", 1, ASSEZ_LONG)
                .section("Détail", 2, ASSEZ_LONG)
                .build();

        assertThat(texte.blocks()).extracting(TextBlock::getHeading).containsExactly("Introduction", "Détail");
        assertThat(texte.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2);
    }

    @Test
    void ecarte_sans_bruit_une_section_dont_le_corps_est_vide() {
        ExtractedText texte = new ExtractedTextBuilder()
                .section("Un titre suivi de rien", 1, "   \n  ")
                .section("Le vrai contenu", 1, ASSEZ_LONG)
                .build();

        assertThat(texte.blocks()).extracting(TextBlock::getHeading).containsExactly("Le vrai contenu");
    }

    @Test
    void un_document_sans_titre_donne_un_unique_bloc() {
        ExtractedText texte = new ExtractedTextBuilder().untitled(ASSEZ_LONG).build();

        assertThat(texte.blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEmpty();
            assertThat(bloc.getText()).isEqualTo(ASSEZ_LONG);
        });
    }

    @Test
    void refuse_de_construire_quand_toutes_les_sections_ont_ete_ecartees() {
        ExtractedTextBuilder blocs =
                new ExtractedTextBuilder().section("Titre seul", 1, "").untitled("  ");

        assertThatExceptionOfType(UnextractableDocumentException.class).isThrownBy(blocs::build);
    }
}
