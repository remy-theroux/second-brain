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

/**
 * Document déposé dans la base de connaissance d'un compte.
 *
 * <p>Le constructeur est privé : un document ne naît que par {@link #upload}, ce qui tient
 * l'invariant « un document naît en attente de traitement ». Le compte propriétaire est
 * référencé par son identifiant et non par un {@code @ManyToOne} — deux agrégats de deux
 * contextes bornés ne se tiennent pas par une association JPA.
 *
 * <p>{@code filename} n'est qu'un libellé d'affichage : ce qui identifie le contenu, c'est
 * {@link Checksum}. Le fichier d'origine, lui, ne vit pas ici mais derrière le port
 * {@code DocumentStorage}, sous le nom de l'identifiant du document.
 *
 * <p>Les annotations JPA dans le domaine sont l'écart assumé déjà acté pour {@code User}
 * (voir ADR-0002) : pas de classe miroir ni de mapper. L'écart
 * s'arrête là — l'entité ignore comment son {@link Checksum} atteint sa colonne.
 */
@Entity
@Table(name = "knowledge_documents")
public class Document {

    /** Ce que la plupart des systèmes de fichiers acceptent comme nom. */
    public static final int MAX_FILENAME_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    // length explicite partout : Hibernate tourne en ddl-auto=validate et compare les
    // métadonnées de l'entité au schéma créé par Flyway.
    @Column(nullable = false, length = MAX_FILENAME_LENGTH)
    private String filename;

    // STRING et non ORDINAL : une colonne lisible en base, et l'ordre de déclaration de
    // l'énumération cesse d'être une donnée du schéma.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentFormat format;

    // Pas de @Convert ici : projeter Checksum sur une colonne texte est un détail
    // d'infrastructure, appliqué par un converter autoApply (knowledge.infrastructure.persistence).
    @Column(nullable = false, length = Checksum.LENGTH)
    private Checksum checksum;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DocumentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Document() {
        // requis par JPA
    }

    private Document(UUID ownerId, String filename, DocumentFormat format, Checksum checksum, long sizeBytes) {
        this.ownerId = ownerId;
        this.filename = filename;
        this.format = format;
        this.checksum = checksum;
        this.sizeBytes = sizeBytes;
        this.status = DocumentStatus.PENDING;
    }

    /**
     * Enregistre un document fraîchement déposé, en attente de traitement.
     *
     * <p>Le nom est tronqué plutôt que refusé : un nom trop long est un désagrément
     * d'affichage, pas une raison de rejeter un contenu par ailleurs valide.
     */
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
