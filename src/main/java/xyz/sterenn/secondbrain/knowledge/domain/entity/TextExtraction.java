package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

@Entity
@Table(name = "knowledge_text_extractions")
public class TextExtraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    // unique : c'est cette contrainte qui impose au handler d'effacer avant d'écrire, une
    // redélivrance AMQP étant toujours possible.
    @Column(name = "document_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID documentId;

    // EAGER : open-in-view est à false, une collection paresseuse ne ferait que déplacer
    // l'échec hors de la transaction du bus.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "knowledge_text_blocks",
            joinColumns = @JoinColumn(name = "text_extraction_id", nullable = false))
    @OrderColumn(name = "block_position")
    private List<TextBlock> blocks = new ArrayList<>();

    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt;

    protected TextExtraction() {}

    private TextExtraction(UUID documentId, List<TextBlock> blocks, Instant extractedAt) {
        this.documentId = documentId;
        this.blocks = blocks;
        this.extractedAt = extractedAt;
    }

    public static TextExtraction of(UUID documentId, ExtractedText text, Instant extractedAt) {
        Objects.requireNonNull(documentId, "Le document dont ce texte est extrait est obligatoire");
        Objects.requireNonNull(text, "Le texte extrait est obligatoire");
        Objects.requireNonNull(extractedAt, "L'instant de l'extraction est obligatoire");
        return new TextExtraction(documentId, new ArrayList<>(text.blocks()), extractedAt);
    }

    public ExtractedText text() {
        return new ExtractedText(blocks);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public List<TextBlock> getBlocks() {
        return List.copyOf(blocks);
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }
}
