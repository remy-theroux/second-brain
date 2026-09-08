package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

class DocumentReplacementTest {

    private static final Checksum FIRST = Checksum.of("premier".getBytes(StandardCharsets.UTF_8));
    private static final Checksum SECOND = Checksum.of("second".getBytes(StandardCharsets.UTF_8));

    private static Document aDocument() {
        Document document = Document.upload(UUID.randomUUID(), "rapport.pdf", DocumentFormat.PDF, FIRST, 12L);
        document.markTextExtracted();
        document.markIndexed();
        return document;
    }

    @Test
    void carries_the_new_content_and_goes_back_to_pending() {
        Document document = aDocument();

        document.replaceContent("rapport-v2.pdf", DocumentFormat.PDF, SECOND, 34L);

        assertThat(document.getFilename()).isEqualTo("rapport-v2.pdf");
        assertThat(document.getChecksum()).isEqualTo(SECOND);
        assertThat(document.getSizeBytes()).isEqualTo(34L);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void clears_the_failure_reason_of_a_previous_attempt() {
        Document document = aDocument();
        document.markProcessingFailed("Ce document ne contient aucun texte exploitable.");

        document.replaceContent("rapport.pdf", DocumentFormat.PDF, SECOND, 34L);

        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void accepts_a_content_of_another_format() {
        Document document = aDocument();

        document.replaceContent("rapport.md", DocumentFormat.MARKDOWN, SECOND, 34L);

        assertThat(document.getFormat()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void bounds_a_filename_longer_than_the_column() {
        Document document = aDocument();
        String tooLong = "n".repeat(Document.MAX_FILENAME_LENGTH + 10) + ".pdf";

        document.replaceContent(tooLong, DocumentFormat.PDF, SECOND, 34L);

        assertThat(document.getFilename()).hasSize(Document.MAX_FILENAME_LENGTH);
    }

    @Test
    void rejects_an_empty_content() {
        Document document = aDocument();

        assertThatThrownBy(() -> document.replaceContent("rapport.pdf", DocumentFormat.PDF, SECOND, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_filename() {
        Document document = aDocument();

        assertThatThrownBy(() -> document.replaceContent("  ", DocumentFormat.PDF, SECOND, 34L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
