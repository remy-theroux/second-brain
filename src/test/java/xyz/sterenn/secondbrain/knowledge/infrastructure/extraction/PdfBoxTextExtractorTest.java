package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PdfBoxTextExtractorTest {

    private final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

    @Test
    void knows_how_to_read_the_pdf_format() {
        assertThat(extractor.format()).isEqualTo(DocumentFormat.PDF);
    }

    @Test
    void splits_a_pdf_with_bookmarks_into_one_section_per_bookmark() {
        ExtractedText text = extractor.extract(Fixtures.read("signets.pdf"));

        assertThat(text.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly("", "Premiere partie", "Seconde partie");
    }

    @Test
    void keeps_outside_any_section_the_cover_page_that_precedes_the_first_bookmark() {
        ExtractedText text = extractor.extract(Fixtures.read("signets.pdf"));

        assertThat(text.blocks()).first().satisfies(block -> {
            assertThat(block.getHeadingLevel()).isZero();
            assertThat(block.getText()).contains("Page de garde");
        });
    }

    @Test
    void never_returns_the_text_of_the_same_page_twice() {
        ExtractedText text = extractor.extract(Fixtures.read("signets.pdf"));

        assertThat(text.blocks().get(1).getText()).doesNotContain("Page de garde");
        assertThat(text.blocks().get(2).getText()).doesNotContain("premiere partie");
    }

    @Test
    void guesses_the_headings_of_a_pdf_without_bookmarks_from_the_font_size() {
        ExtractedText text = extractor.extract(Fixtures.read("sans-signets.pdf"));

        assertThat(text.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly("Rapport annuel", "Premiere partie", "Seconde partie");
        assertThat(text.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 2);
    }

    @Test
    void rejects_a_scanned_pdf_without_a_text_layer() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extractor.extract(Fixtures.read("numerise.pdf")))
                .withMessageContaining("pas de texte exploitable");
    }

    @Test
    void rejects_a_file_that_is_not_a_pdf() {
        assertThatExceptionOfType(UnreadableDocumentException.class)
                .isThrownBy(() -> extractor.extract("Ceci n'est pas un PDF.".getBytes(UTF_8)))
                .withMessageContaining("n'a pas pu être lu");
    }
}
