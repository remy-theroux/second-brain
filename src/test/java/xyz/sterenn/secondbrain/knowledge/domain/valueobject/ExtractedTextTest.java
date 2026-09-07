package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

class ExtractedTextTest {

    private static final String LONG_ENOUGH = "Un texte assez long pour franchir le plancher des cinquante.";

    @Test
    void keeps_its_blocks_in_the_order_they_arrive() {
        TextBlock first = TextBlock.of("Un", 1, LONG_ENOUGH);
        TextBlock second = TextBlock.of("Deux", 1, LONG_ENOUGH);

        assertThat(new ExtractedText(List.of(first, second)).blocks()).containsExactly(first, second);
    }

    @Test
    void rejects_a_document_without_any_block() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> new ExtractedText(List.of()))
                .withMessageContaining("pas de texte exploitable");
    }

    @Test
    void rejects_a_document_below_the_character_floor() {
        String threeScraps = "3 Page 1";
        assertThat(threeScraps.length()).isLessThan(ExtractionPolicy.MINIMUM_USEFUL_CHARACTERS);

        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> ExtractedText.untitled(threeScraps));
    }

    @Test
    void sums_the_characters_of_all_its_blocks_without_counting_the_headings() {
        ExtractedText text = new ExtractedText(
                List.of(TextBlock.of("Un titre qui ne compte pas", 1, LONG_ENOUGH), TextBlock.untitled(LONG_ENOUGH)));

        assertThat(text.characterCount()).isEqualTo(LONG_ENOUGH.length() * 2);
    }

    @Test
    void is_not_modified_by_the_list_it_was_given() {
        List<TextBlock> mutable = new ArrayList<>(List.of(TextBlock.untitled(LONG_ENOUGH)));
        ExtractedText text = new ExtractedText(mutable);

        mutable.clear();

        assertThat(text.blocks()).hasSize(1);
    }
}
