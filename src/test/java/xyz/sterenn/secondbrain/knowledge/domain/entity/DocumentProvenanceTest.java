package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;

class DocumentProvenanceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final Checksum CHECKSUM = Checksum.of("contenu".getBytes(StandardCharsets.UTF_8));
    private static final Instant MODIFIED_TIME = Instant.parse("2026-09-08T10:15:30Z");

    private static final DriveProvenance PROVENANCE =
            new DriveProvenance("1aBcD", "https://drive.google.com/file/d/1aBcD/view", MODIFIED_TIME);

    @Test
    void a_manually_uploaded_document_has_no_drive_provenance() {
        Document document = Document.upload(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L);

        assertThat(document.getSource()).isEqualTo(DocumentSource.MANUAL);
        assertThat(document.getDriveProvenance()).isEmpty();
    }

    @Test
    void an_imported_document_carries_its_drive_file_and_link() {
        Document document =
                Document.importedFromDrive(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L, PROVENANCE);

        assertThat(document.getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
        assertThat(document.getDriveProvenance()).contains(PROVENANCE);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    void attaching_a_drive_provenance_leaves_the_content_untouched() {
        Document document = Document.upload(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L);
        document.markIndexed();

        document.attachTo(PROVENANCE);

        assertThat(document.getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
        assertThat(document.getDriveProvenance()).contains(PROVENANCE);
        assertThat(document.getChecksum()).isEqualTo(CHECKSUM);
        assertThat(document.getFilename()).isEqualTo("rapport.pdf");
        assertThat(document.getSizeBytes()).isEqualTo(12L);
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
    }

    @Test
    void refuses_to_attach_a_provenance_to_a_document_that_already_has_one() {
        Document document =
                Document.importedFromDrive(OWNER, "rapport.pdf", DocumentFormat.PDF, CHECKSUM, 12L, PROVENANCE);

        assertThatThrownBy(() -> document.attachTo(new DriveProvenance("2eFgH", null, MODIFIED_TIME)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejects_a_provenance_without_a_drive_file() {
        assertThatThrownBy(() -> new DriveProvenance("  ", null, MODIFIED_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
