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

/**
 * Un extrait vectorisé, rangé sous l'identifiant de son document.
 *
 * <p><strong>Une entité, et non une {@code @ElementCollection} d'un agrégat « découpage »</strong>,
 * là où les blocs d'une {@link TextExtraction} en sont une. La différence est réelle : la
 * recherche vectorielle de RAG-8 rendra des extraits un par un, avec leur score. Ils ont
 * besoin d'une identité ; une collection d'éléments n'en a pas.
 *
 * <p>Le rapport à {@link Chunk} est celui de {@link TextExtraction} à {@code ExtractedText} :
 * la logique pure produit l'objet-valeur, l'entité le range. Le découpage n'a pas à savoir
 * qu'il existe une base.
 *
 * <p><strong>La colonne {@code text} porte le corps nu</strong>, jamais le texte préfixé qui
 * est parti au service de vectorisation ({@code Chunk.contextualised}). Ce qui s'affiche à
 * l'écran reste lisible, et changer la forme du préfixe plus tard ne demandera pas de
 * réécrire la base — seulement de revectoriser. La provenance, elle, est dite par
 * {@code heading} et {@code document_id}.
 *
 * <p>Le vecteur est un {@code float[]} annoté, et non un {@link Embedding} projeté par un
 * converter : {@code hibernate-vector} porte le type {@code vector} de pgvector sur un
 * tableau de flottants, et un {@code AttributeConverter} vers une chaîne obligerait
 * PostgreSQL à un transtypage que le pilote ne fait pas. L'objet-valeur reste la seule porte
 * d'entrée et de sortie : {@link #of} n'accepte qu'un {@link Embedding}, {@link #getEmbedding}
 * n'en rend qu'un — la dimension est donc validée par le domaine avant d'atteindre la colonne.
 */
@Entity
@Table(name = "knowledge_text_chunks")
public class TextChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "document_id", nullable = false, columnDefinition = "uuid")
    private UUID documentId;

    // `position` est un mot-clé SQL qu'Hibernate écrirait sans guillemets : `chunk_position`,
    // comme `block_position` du côté de l'extraction, et pour la même raison.
    @Column(name = "chunk_position", nullable = false)
    private int position;

    // La longueur de TextBlock, parce que c'est de là que vient le titre : les deux colonnes
    // ne peuvent pas diverger si l'une nomme la constante de l'autre.
    @Column(nullable = false, length = TextBlock.MAX_HEADING_LENGTH)
    private String heading;

    // columnDefinition explicite : sans lui, Hibernate attendrait un varchar(255) et
    // `ddl-auto: validate` refuserait de démarrer contre une colonne `text`.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    // Le type `vector` de pgvector, apporté par hibernate-vector. La longueur est celle du
    // modèle, déclarée une seule fois dans EmbeddingPolicy : la colonne, l'index et le
    // modèle ne peuvent pas se désaligner par distraction.
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EmbeddingPolicy.DIMENSIONS)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TextChunk() {
        // requis par JPA
    }

    private TextChunk(
            UUID documentId, int position, String heading, String text, float[] embedding, Instant createdAt) {
        this.documentId = documentId;
        this.position = position;
        this.heading = heading;
        this.text = text;
        this.embedding = embedding;
        this.createdAt = createdAt;
    }

    /**
     * Range un extrait et le vecteur qui en a été tiré.
     *
     * <p>{@link Chunk} garantit déjà qu'un extrait a un corps et un titre — éventuellement
     * vide, jamais absent —, et {@link Embedding} qu'un vecteur a la dimension du modèle : il
     * n'y a rien à revalider ici.
     *
     * @throws IllegalArgumentException si la position est négative — c'est une erreur de
     *     programmation de l'appelant, pas un refus métier
     */
    public static TextChunk of(UUID documentId, int position, Chunk chunk, Embedding embedding, Instant createdAt) {
        Objects.requireNonNull(documentId, "Le document dont cet extrait provient est obligatoire");
        Objects.requireNonNull(chunk, "L'extrait est obligatoire");
        Objects.requireNonNull(embedding, "Le vecteur de l'extrait est obligatoire");
        Objects.requireNonNull(createdAt, "L'instant du découpage est obligatoire");
        if (position < 0) {
            throw new IllegalArgumentException("La position d'un extrait part de zéro, reçue : " + position);
        }
        return new TextChunk(documentId, position, chunk.heading(), chunk.text(), embedding.values(), createdAt);
    }

    /** L'extrait du domaine, tel qu'il a été rangé. */
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

    /** L'objet-valeur, jamais le tableau : c'est lui qui porte la dimension du modèle. */
    public Embedding getEmbedding() {
        return Embedding.of(embedding);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
