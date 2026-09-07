package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

class ExtractorCoverageTest {

    private record DummyExtractor(DocumentFormat format) implements DocumentTextExtractor {
        @Override
        public ExtractedText extract(byte[] content) {
            throw new UnsupportedOperationException("Cet extracteur n'est là que pour son format");
        }
    }

    private static List<DocumentTextExtractor> coversTheTextualType() {
        return DocumentFormat.of(DocumentType.TEXTUAL).stream()
                .map(format -> (DocumentTextExtractor) new DummyExtractor(format))
                .toList();
    }

    @Test
    void accepts_one_extractor_per_textual_format() {
        assertThat(ExtractDocumentTextHandler.indexedByFormat(coversTheTextualType()))
                .containsOnlyKeys(DocumentFormat.of(DocumentType.TEXTUAL).toArray(DocumentFormat[]::new));
    }

    @Test
    void rejects_a_textual_format_without_an_extractor() {
        List<DocumentTextExtractor> incomplete = coversTheTextualType().stream()
                .filter(extractor -> extractor.format() != DocumentFormat.DOCX)
                .toList();

        assertThatIllegalStateException()
                .isThrownBy(() -> ExtractDocumentTextHandler.indexedByFormat(incomplete))
                .withMessageContaining("DOCX");
    }

    @Test
    void rejects_two_extractors_for_the_same_format() {
        List<DocumentTextExtractor> duplicate = new ArrayList<>(coversTheTextualType());
        duplicate.add(new DummyExtractor(DocumentFormat.PDF));

        assertThatIllegalStateException()
                .isThrownBy(() -> ExtractDocumentTextHandler.indexedByFormat(duplicate))
                .withMessageContaining("PDF");
    }

    @Test
    void requires_an_extractor_only_from_formats_of_the_textual_type() {
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL)).containsExactlyElementsOf(List.of(DocumentFormat.values()));
    }
}
