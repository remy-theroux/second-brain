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

    /**
     * De quoi porter le plus long des messages de refus métier, avec de la marge. Ce qui
     * dépasse est tronqué : un motif est une explication, pas une trace d'exécution.
     */
    public static final int MAX_ERROR_MESSAGE_LENGTH = 500;

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

    // Nullable, à l'inverse de tout le reste de cette entité : un document qui n'a pas
    // échoué n'a pas de motif, et une chaîne vide voudrait dire « échoué sans raison ».
    @Column(name = "error_message", length = MAX_ERROR_MESSAGE_LENGTH)
    private String errorMessage;

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

    /**
     * Le texte de ce document a été extrait et rangé.
     *
     * <p>Efface le motif d'un échec précédent : un document réextrait avec succès ne doit
     * pas garder l'explication de ce qui a raté la fois d'avant.
     *
     * <p>Aucun garde sur l'état de départ, volontairement. RAG-7 réextraira depuis
     * {@code EXTRACTED} comme depuis {@code FAILED} ; un garde posé aujourd'hui serait à
     * retirer demain.
     */
    public void markTextExtracted() {
        this.status = DocumentStatus.EXTRACTED;
        this.errorMessage = null;
    }

    /**
     * Le traitement de ce document a échoué, pour la raison donnée.
     *
     * <p>Le motif est <strong>affichable tel quel</strong> : c'est l'appelant qui garantit
     * qu'il ne transporte pas une trace technique — voir {@code KnowledgeEventListener} et
     * ADR-0028. Ici, on garantit seulement qu'il existe et qu'il tient dans sa colonne.
     *
     * @throws IllegalArgumentException si le motif est absent ou vide — un échec sans motif
     *     n'apprend rien de plus qu'un document resté en attente
     */
    public void markExtractionFailed(String reason) {
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

    /** {@code null} tant qu'aucun traitement n'a échoué. */
    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
