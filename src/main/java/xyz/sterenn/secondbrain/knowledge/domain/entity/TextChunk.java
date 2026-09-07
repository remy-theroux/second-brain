package xyz.sterenn.secondbrain.knowledge.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

@Entity
@Table(name = "knowledge_text_chunks")
public class TextChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "document_id", nullable = false, columnDefinition = "uuid")
    private UUID documentId;

    // `position` is a SQL keyword that Hibernate would write unquoted.
    @Column(name = "chunk_position", nullable = false)
    private int position;

    @Column(nullable = false, length = TextBlock.MAX_HEADING_LENGTH)
    private String heading;

    // Explicit columnDefinition: without it, Hibernate would expect a varchar(255) and
    // `ddl-auto: validate` would refuse to start against a `text` column.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingPolicy.DIMENSIONS)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TextChunk() {}

    private TextChunk(
            UUID documentId, int position, String heading, String text, float[] embedding, Instant createdAt) {
        this.documentId = documentId;
        this.position = position;
        this.heading = heading;
        this.text = text;
        this.embedding = embedding;
        this.createdAt = createdAt;
    }

    public static TextChunk of(UUID documentId, int position, Chunk chunk, Embedding embedding, Instant createdAt) {
        Objects.requireNonNull(documentId, "The document this chunk comes from is required");
        Objects.requireNonNull(chunk, "A chunk is required");
        Objects.requireNonNull(embedding, "The chunk vector is required");
        Objects.requireNonNull(createdAt, "The chunking instant is required");
        if (position < 0) {
            throw new IllegalArgumentException("A chunk position starts at zero, got: " + position);
        }
        return new TextChunk(documentId, position, chunk.heading(), chunk.text(), embedding.values(), createdAt);
    }

    public Chunk chunk() {
        return new Chunk(heading, text);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getPosition() {
        return position;
    }

    public String getHeading() {
        return heading;
    }

    public String getText() {
        return text;
    }

    public Embedding getEmbedding() {
        return Embedding.of(embedding);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
