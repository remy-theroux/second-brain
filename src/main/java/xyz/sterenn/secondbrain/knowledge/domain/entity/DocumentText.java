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

/**
 * Le texte extrait d'un document, dans la forme commune aux quatre formats acceptés.
 *
 * <p><strong>Agrégat distinct de {@link Document}</strong>, et non des colonnes de plus sur
 * lui : il naît plus tard, et il est remplacé en entier à chaque réextraction. Les deux se
 * référencent donc par identifiant, jamais par {@code @ManyToOne} — ADR-0006.
 *
 * <p>Les blocs sont une {@code @ElementCollection} et non des entités : un bloc n'a pas
 * d'identité propre, il n'existe que par le texte qui le contient, et rien ne le désigne de
 * l'extérieur. Sa position est portée par {@code @OrderColumn} plutôt que par un champ de
 * {@link TextBlock} : elle appartient à la liste, pas au bloc — un bloc extrait de son
 * document reste le même bloc.
 *
 * <p>{@code EAGER}, à contre-courant de l'habitude : {@code open-in-view} est à {@code false}
 * et personne ne charge un {@code DocumentText} sans vouloir ses blocs. Une collection
 * paresseuse ne ferait que déplacer l'échec hors de la transaction du bus.
 */
@Entity
@Table(name = "knowledge_document_texts")
public class DocumentText {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    // unique = true : un document a un texte, jamais deux. C'est cette contrainte qui impose
    // au handler d'effacer avant d'écrire — une redélivrance AMQP est toujours possible.
    @Column(name = "document_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID documentId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "knowledge_document_blocks",
            joinColumns = @JoinColumn(name = "document_text_id", nullable = false))
    @OrderColumn(name = "block_position")
    private List<TextBlock> blocks = new ArrayList<>();

    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt;

    protected DocumentText() {
        // requis par JPA
    }

    private DocumentText(UUID documentId, List<TextBlock> blocks, Instant extractedAt) {
        this.documentId = documentId;
        this.blocks = blocks;
        this.extractedAt = extractedAt;
    }

    /**
     * Range un texte fraîchement extrait sous l'identifiant de son document.
     *
     * <p>{@link ExtractedText} garantit déjà qu'il n'est ni vide ni sous le plancher : il n'y
     * a rien à revalider ici, seulement à recopier dans une liste que JPA peut gérer.
     */
    public static DocumentText of(UUID documentId, ExtractedText text, Instant extractedAt) {
        Objects.requireNonNull(documentId, "Le document dont ce texte est extrait est obligatoire");
        Objects.requireNonNull(text, "Le texte extrait est obligatoire");
        Objects.requireNonNull(extractedAt, "L'instant de l'extraction est obligatoire");
        return new DocumentText(documentId, new ArrayList<>(text.blocks()), extractedAt);
    }

    /** Le format du domaine, tel que RAG-5 le consommera. */
    public ExtractedText text() {
        return new ExtractedText(blocks);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    /** Copie : la liste interne est gérée par Hibernate, personne d'autre n'y touche. */
    public List<TextBlock> getBlocks() {
        return List.copyOf(blocks);
    }

    public Instant getExtractedAt() {
        return extractedAt;
    }
}
