package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Entity
@Table(name = "knowledge_documents")
public class Document {

    public static final int MAX_FILENAME_LENGTH = 255;

    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(nullable = false, length = MAX_FILENAME_LENGTH)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentFormat format;

    @Column(nullable = false, length = Checksum.LENGTH)
    private Checksum checksum;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status;

    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentSource source;

    @Column(name = "drive_file_id", length = DriveProvenance.MAX_FILE_ID_LENGTH)
    private String driveFileId;

    @Column(name = "drive_web_view_link", columnDefinition = "text")
    private String driveWebViewLink;

    // Nothing reads it before DRIVE-5: it is stored now because a column added later would
    // mean walking the whole Drive again for the documents already imported.
    @Column(name = "drive_modified_time")
    private Instant driveModifiedTime;

    @Column(name = "watched_folder_id", columnDefinition = "uuid")
    private UUID watchedFolderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // The PUT route can mutate a document the worker is still processing: without this, the
    // worker's final save would silently write the previous version's content back.
    @Version
    @Column(nullable = false)
    private long version;

    protected Document() {}

    private Document(UUID ownerId, String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        this.ownerId = ownerId;
        this.filename = filename;
        this.format = format;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
        this.status = DocumentStatus.PENDING;
        this.source = DocumentSource.MANUAL;
    }

    public static Document upload(
            UUID ownerId, String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        if (ownerId == null) {
            throw new IllegalArgumentException("The document owner is required");
        }
        return new Document(ownerId, boundedFilename(filename), format, checksum, requirePositive(sizeBytes));
    }

    public static Document importedFromDrive(
            UUID ownerId,
            String filename,
            DocumentFormat format,
            Checksum checksum,
            long sizeBytes,
            DriveProvenance provenance,
            UUID watchedFolderId) {
        Document document = upload(ownerId, filename, format, checksum, sizeBytes);
        document.attachTo(provenance, watchedFolderId);
        return document;
    }

    /**
     * The content is left exactly as it was: a document already in the base gains its Drive
     * origin without a second copy and without anything to process again.
     */
    public void attachTo(DriveProvenance provenance, UUID watchedFolderId) {
        if (provenance == null) {
            throw new IllegalArgumentException("The Drive origin of a document is required");
        }
        if (watchedFolderId == null) {
            throw new IllegalArgumentException("The watched folder a document came through is required");
        }
        if (this.driveFileId != null) {
            throw new IllegalStateException("A document already carries a Drive file: " + this.driveFileId);
        }
        this.source = DocumentSource.GOOGLE_DRIVE;
        this.driveFileId = provenance.fileId();
        this.driveWebViewLink = provenance.webViewLink();
        this.driveModifiedTime = provenance.modifiedTime();
        this.watchedFolderId = watchedFolderId;
    }

    public void replaceContent(String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        String bounded = boundedFilename(filename);
        long size = requirePositive(sizeBytes);
        this.filename = bounded;
        this.format = format;
        this.checksum = checksum;
        this.sizeBytes = size;
        this.status = DocumentStatus.PENDING;
        this.errorMessage = null;
    }

    private static String boundedFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("A filename is required");
        }
        String trimmed = filename.trim();
        return trimmed.length() > MAX_FILENAME_LENGTH ? trimmed.substring(0, MAX_FILENAME_LENGTH) : trimmed;
    }

    private static long requirePositive(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("An empty document has nothing to bring to the knowledge base");
        }
        return sizeBytes;
    }

    public void markTextExtracted() {
        this.status = DocumentStatus.EXTRACTED;
        this.errorMessage = null;
    }

    public void markIndexed() {
        this.status = DocumentStatus.READY;
        this.errorMessage = null;
    }

    public void markProcessingFailed(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A failure without a reason teaches nothing: the reason is required");
        }
        String strippedReason = reason.strip();
        this.status = DocumentStatus.FAILED;
        this.errorMessage = strippedReason.length() > MAX_ERROR_MESSAGE_LENGTH
                ? strippedReason.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : strippedReason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getFilename() {
        return filename;
    }

    public DocumentFormat getFormat() {
        return format;
    }

    public Checksum getChecksum() {
        return checksum;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public DocumentSource getSource() {
        return source;
    }

    public UUID getWatchedFolderId() {
        return watchedFolderId;
    }

    public Optional<DriveProvenance> getDriveProvenance() {
        return Optional.ofNullable(driveFileId)
                .map(fileId -> new DriveProvenance(fileId, driveWebViewLink, driveModifiedTime));
    }
}
