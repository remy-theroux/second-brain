package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

/** Voir ADR-0002 : l'écart qui autorise les annotations JPA dans le domaine. */
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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Document() {}

    private Document(UUID ownerId, String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        this.ownerId = ownerId;
        this.filename = filename;
        this.format = format;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
        this.status = DocumentStatus.PENDING;
    }

    public static Document upload(
            UUID ownerId, String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Le propriétaire du document est obligatoire");
        }
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Le nom du fichier est obligatoire");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("Un document vide n'a rien à apporter à la base de connaissance");
        }
        String nomBorne = filename.trim();
        if (nomBorne.length() > MAX_FILENAME_LENGTH) {
            nomBorne = nomBorne.substring(0, MAX_FILENAME_LENGTH);
        }
        return new Document(ownerId, nomBorne, format, checksum, sizeBytes);
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
            throw new IllegalArgumentException("Un échec sans motif n'apprend rien : le motif est obligatoire");
        }
        String motif = reason.strip();
        this.status = DocumentStatus.FAILED;
        this.errorMessage =
                motif.length() > MAX_ERROR_MESSAGE_LENGTH ? motif.substring(0, MAX_ERROR_MESSAGE_LENGTH) : motif;
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
}
