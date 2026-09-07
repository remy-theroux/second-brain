package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class CommonmarkTextExtractorTest {

    private final CommonmarkTextExtractor extractor = new CommonmarkTextExtractor();

    @Test
    void knows_how_to_read_the_markdown_format() {
        assertThat(extractor.format()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void attaches_each_block_to_the_heading_of_its_section() {
        ExtractedText text = extractor.extract(Fixtures.read("structure.md"));

        assertThat(text.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly(
                        "Journal de bord", "Première section", "Un détail de la première section", "Seconde section");
    }

    @Test
    void returns_the_level_of_each_heading() {
        assertThat(extractor.extract(Fixtures.read("structure.md")).blocks())
                .extracting(TextBlock::getHeadingLevel)
                .containsExactly(1, 2, 3, 2);
    }

    @Test
    void does_not_take_the_hash_of_a_code_block_for_a_heading() {
        ExtractedText text = extractor.extract(Fixtures.read("structure.md"));

        assertThat(text.blocks())
                .extracting(TextBlock::getHeading)
                .doesNotContain("ceci est un commentaire shell, pas une section");
        assertThat(text.blocks().getLast().getText()).contains("echo bonjour");
    }

    @Test
    void a_markdown_without_a_heading_yields_a_single_block() {
        ExtractedText text = extractor.extract(Fixtures.read("sans-titres.md"));

        assertThat(text.blocks()).singleElement().satisfies(block -> {
            assertThat(block.getHeading()).isEmpty();
            assertThat(block.getHeadingLevel()).isZero();
        });
    }

    @Test
    void returns_the_text_and_not_the_markup() {
        String extracted = extractor
                .extract(Fixtures.read("sans-titres.md"))
                .blocks()
                .getFirst()
                .getText();

        assertThat(extracted).contains("emphase").doesNotContain("*emphase*").doesNotContain("`");
    }

    @Test
    void rejects_a_markdown_that_has_only_headings() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extractor.extract("# Un titre\n\n## Un autre\n".getBytes(UTF_8)));
    }
}
