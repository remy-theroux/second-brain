package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TextBlockTest {

    @Test
    void keeps_the_heading_its_level_and_the_text() {
        TextBlock block = TextBlock.of("Introduction", 1, "Le corps de la section.");

        assertThat(block.getHeading()).isEqualTo("Introduction");
        assertThat(block.getHeadingLevel()).isEqualTo(1);
        assertThat(block.getText()).isEqualTo("Le corps de la section.");
    }

    @Test
    void an_untitled_block_carries_level_zero() {
        TextBlock block = TextBlock.untitled("Tout le texte du document.");

        assertThat(block.getHeading()).isEmpty();
        assertThat(block.getHeadingLevel()).isZero();
    }

    @Test
    void brings_the_level_back_to_zero_when_the_heading_is_blank() {
        assertThat(TextBlock.of("   ", 3, "Du texte.").getHeadingLevel()).isZero();
    }

    @Test
    void rejects_a_level_outside_one_to_six_for_a_filled_heading() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TextBlock.of("Titre", 7, "Du texte."))
                .withMessageContaining("1 to 6");
    }

    @Test
    void rejects_a_block_whose_text_is_empty_once_normalised() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TextBlock.untitled("  \n\n \t "))
                .withMessageContaining("without text");
    }

    @Test
    void normalises_the_line_endings_and_the_trailing_spaces() {
        TextBlock block = TextBlock.untitled("Première ligne   \r\nDeuxième ligne\r");

        assertThat(block.getText()).isEqualTo("Première ligne\nDeuxième ligne");
    }

    @Test
    void keeps_the_paragraph_boundary_but_not_the_layout() {
        TextBlock block = TextBlock.untitled("Paragraphe un.\n\n\n\n\nParagraphe deux.");

        assertThat(block.getText()).isEqualTo("Paragraphe un.\n\nParagraphe deux.");
    }

    @Test
    void erases_the_soft_hyphen_that_would_cut_the_words() {
        assertThat(TextBlock.untitled("consti\u00ADtution").getText()).isEqualTo("constitution");
    }

    @Test
    void brings_the_non_breaking_space_back_to_an_ordinary_space_without_gluing_the_words() {
        assertThat(TextBlock.untitled("Article\u00A0premier : le texte.").getText())
                .isEqualTo("Article premier : le texte.");
    }

    @Test
    void erases_the_byte_order_mark_at_the_head_of_the_file() {
        assertThat(TextBlock.untitled("\uFEFFPremière ligne du fichier.").getText())
                .isEqualTo("Première ligne du fichier.");
    }

    @Test
    void flattens_the_whitespace_of_a_heading_onto_a_single_line() {
        TextBlock block = TextBlock.of("Chapitre\n premier   ", 1, "Du texte.");

        assertThat(block.getHeading()).isEqualTo("Chapitre premier");
    }

    @Test
    void truncates_an_overlong_heading_rather_than_rejecting_it() {
        String veryLong = "T".repeat(TextBlock.MAX_HEADING_LENGTH + 42);

        assertThat(TextBlock.of(veryLong, 1, "Du texte.").getHeading()).hasSize(TextBlock.MAX_HEADING_LENGTH);
    }

    @Test
    void two_blocks_with_the_same_content_are_equal() {
        assertThat(TextBlock.of("Titre", 2, "Corps.")).isEqualTo(TextBlock.of("Titre", 2, "Corps."));
    }
}
