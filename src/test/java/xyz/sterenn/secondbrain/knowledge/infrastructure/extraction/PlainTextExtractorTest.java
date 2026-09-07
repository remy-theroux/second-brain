package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PlainTextExtractorTest {

    private final PlainTextExtractor extractor = new PlainTextExtractor();

    @Test
    void knows_how_to_read_the_text_format() {
        assertThat(extractor.format()).isEqualTo(DocumentFormat.TEXT);
    }

    @Test
    void returns_a_single_block_without_a_heading() {
        ExtractedText text = extractor.extract(Fixtures.read("brut.txt"));

        assertThat(text.blocks()).singleElement().satisfies(block -> {
            assertThat(block.getHeading()).isEmpty();
            assertThat(block.getHeadingLevel()).isZero();
            assertThat(block.getText()).contains("Notes prises pendant la réunion");
        });
    }

    @Test
    void preserves_the_boundary_between_paragraphs() {
        assertThat(extractor.extract(Fixtures.read("brut.txt")).blocks())
                .first()
                .extracting(TextBlock::getText, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("\n\n");
    }

    @Test
    void reads_an_iso_8859_1_encoded_file_rather_than_failing() {
        byte[] latin1 = "Une réunion très intéressante, tenue à Bruxelles en février.".getBytes(ISO_8859_1);

        assertThat(extractor.extract(latin1).blocks().getFirst().getText()).contains("très intéressante");
    }

    @Test
    void rejects_a_file_that_says_nothing() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extractor.extract("   \n\n  ".getBytes(UTF_8)));
    }
}
