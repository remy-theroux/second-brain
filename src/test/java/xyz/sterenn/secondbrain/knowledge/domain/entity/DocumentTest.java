package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

class DocumentTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Checksum CHECKSUM = Checksum.of("contenu".getBytes(StandardCharsets.UTF_8));

    @Test
    void is_born_pending_processing() {
        Document document = Document.upload(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void carries_its_owner_its_name_its_format_and_its_checksum() {
        Document document = Document.upload(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L);

        assertThat(document.getOwnerId()).isEqualTo(OWNER);
        assertThat(document.getFilename()).isEqualTo("rapport.pdf");
        assertThat(document.getFormat()).isEqualTo(DocumentFormat.PDF);
        assertThat(document.getChecksum()).isEqualTo(CHECKSUM);
        assertThat(document.getSizeBytes()).isEqualTo(12L);
    }

    @Test
    void truncates_an_overlong_filename_rather_than_rejecting_the_content() {
        String endlessFilename = "a".repeat(400) + ".pdf";

        Document document = Document.upload(OWNER, endlessFilename, DocumentFormat.PDF, CHECKSUM, 12L);

        assertThat(document.getFilename()).hasSize(Document.MAX_FILENAME_LENGTH);
    }

    @Test
    void rejects_an_empty_document() {
        assertThatThrownBy(() -> Document.upload(OWNER, "vide.txt", DocumentFormat.TEXT, CHECKSUM, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_document_without_an_owner() {
        assertThatThrownBy(() -> Document.upload(null, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_document_without_a_name() {
        assertThatThrownBy(() -> Document.upload(OWNER, "   ", DocumentFormat.PDF, CHECKSUM, 12L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_extracted_document_carries_the_extracted_status_and_no_reason() {
        Document document = anUploadedDocument();

        document.markTextExtracted();

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void a_failed_document_carries_the_failed_status_and_its_reason() {
        Document document = anUploadedDocument();

        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(document.getErrorMessage()).isEqualTo("Ce document ne contient pas de texte exploitable.");
    }

    @Test
    void a_successful_extraction_clears_the_reason_of_the_previous_failure() {
        Document document = anUploadedDocument();
        document.markProcessingFailed("Un premier échec.");

        document.markTextExtracted();

        assertThat(document.getErrorMessage()).isNull();
    }

    @Test
    void rejects_a_failure_without_a_reason() {
        Document document = anUploadedDocument();

        assertThatThrownBy(() -> document.markProcessingFailed("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void truncates_a_reason_too_long_for_its_column() {
        Document document = anUploadedDocument();

        document.markProcessingFailed("M".repeat(Document.MAX_ERROR_MESSAGE_LENGTH + 42));

        assertThat(document.getErrorMessage()).hasSize(Document.MAX_ERROR_MESSAGE_LENGTH);
    }

    private static Document anUploadedDocument() {
        return Document.upload(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L);
    }
}
