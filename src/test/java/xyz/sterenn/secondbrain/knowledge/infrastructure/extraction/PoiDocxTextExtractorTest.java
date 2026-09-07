package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PoiDocxTextExtractorTest {

    private static final String BODY =
            "Un corps de section assez long pour franchir le plancher de cinquante caractères.";

    private final PoiDocxTextExtractor extractor = new PoiDocxTextExtractor();

    @Test
    void knows_how_to_read_the_docx_format() {
        assertThat(extractor.format()).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void attaches_each_block_to_the_heading_of_its_section() {
        ExtractedText text = extractor.extract(Fixtures.read("titres.docx"));

        assertThat(text.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly(
                        "Rapport annuel", "Première partie", "Un détail de la première partie", "Seconde partie");
        assertThat(text.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 3, 2);
    }

    @Test
    void preserves_the_boundary_between_two_paragraphs_of_the_same_section() {
        ExtractedText text = extractor.extract(Fixtures.read("titres.docx"));

        assertThat(text.blocks().get(1).getText()).contains("\n\n");
    }

    @Test
    void recognises_a_heading_style_named_in_french() throws IOException {
        // A French Word sometimes gives an opaque style id and only names the style in
        // <w:name>: that is the fallback this test exercises.
        byte[] docx = aDocxWithACustomStyle("Style42", "Titre 1", "Chapitre premier", BODY);

        assertThat(extractor.extract(docx).blocks()).singleElement().satisfies(block -> {
            assertThat(block.getHeading()).isEqualTo("Chapitre premier");
            assertThat(block.getHeadingLevel()).isEqualTo(1);
        });
    }

    @Test
    void rejects_a_file_that_is_not_a_docx() {
        assertThatExceptionOfType(UnreadableDocumentException.class)
                .isThrownBy(() -> extractor.extract("Ceci n'est pas un document Word.".getBytes(UTF_8)))
                .withMessageContaining("n'a pas pu être lu");
    }

    @Test
    void rejects_a_docx_that_says_nothing() throws IOException {
        try (XWPFDocument empty = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            empty.createParagraph().createRun().setText("   ");
            empty.write(output);

            assertThatExceptionOfType(UnextractableDocumentException.class)
                    .isThrownBy(() -> extractor.extract(output.toByteArray()));
        }
    }

    private static byte[] aDocxWithACustomStyle(String styleId, String styleName, String heading, String body)
            throws IOException {
        try (XWPFDocument docx = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFStyles styles = docx.createStyles();
            CTStyle definition = CTStyle.Factory.newInstance();
            definition.setStyleId(styleId);
            definition.addNewName().setVal(styleName);
            styles.addStyle(new XWPFStyle(definition));

            XWPFParagraph headingParagraph = docx.createParagraph();
            headingParagraph.setStyle(styleId);
            headingParagraph.createRun().setText(heading);
            docx.createParagraph().createRun().setText(body);

            docx.write(output);
            return output.toByteArray();
        }
    }
}
