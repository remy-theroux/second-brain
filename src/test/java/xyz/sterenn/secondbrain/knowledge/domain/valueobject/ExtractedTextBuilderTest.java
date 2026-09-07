package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

class ExtractedTextBuilderTest {

    private static final String LONG_ENOUGH = "Un texte assez long pour franchir le plancher des cinquante.";

    @Test
    void assembles_the_sections_in_order() {
        ExtractedText text = new ExtractedTextBuilder()
                .section("Introduction", 1, LONG_ENOUGH)
                .section("Détail", 2, LONG_ENOUGH)
                .build();

        assertThat(text.blocks()).extracting(TextBlock::getHeading).containsExactly("Introduction", "Détail");
        assertThat(text.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2);
    }

    @Test
    void silently_discards_a_section_whose_body_is_empty() {
        ExtractedText text = new ExtractedTextBuilder()
                .section("Un titre suivi de rien", 1, "   \n  ")
                .section("Le vrai contenu", 1, LONG_ENOUGH)
                .build();

        assertThat(text.blocks()).extracting(TextBlock::getHeading).containsExactly("Le vrai contenu");
    }

    @Test
    void an_untitled_document_yields_a_single_block() {
        ExtractedText text = new ExtractedTextBuilder().untitled(LONG_ENOUGH).build();

        assertThat(text.blocks()).singleElement().satisfies(block -> {
            assertThat(block.getHeading()).isEmpty();
            assertThat(block.getText()).isEqualTo(LONG_ENOUGH);
        });
    }

    @Test
    void refuses_to_build_when_every_section_has_been_discarded() {
        ExtractedTextBuilder builder =
                new ExtractedTextBuilder().section("Titre seul", 1, "").untitled("  ");

        assertThatExceptionOfType(UnextractableDocumentException.class).isThrownBy(builder::build);
    }
}
