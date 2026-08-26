package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;

/**
 * Projection de lecture d'un document et de ce qui en a été extrait — ce que l'écran de
 * détail affiche.
 *
 * <p>Plus riche que {@link DocumentView}, et c'est voulu : la liste sert à reconnaître un
 * dépôt, le détail à le lire. Format et taille apparaissent ici et pas là-bas.
 *
 * <p>{@code type} est la <strong>typologie</strong>, déduite du format (ADR-0029) : c'est elle
 * qui dit au front quel rendu appliquer à {@code extraction}. Elle voyage en code, pas en
 * libellé, comme tout ce que l'API sérialise d'une énumération.
 *
 * <p>{@code extraction} est omis quand il est nul : un document en attente ou en échec n'en a
 * pas, et un {@code null} explicite ne dirait rien de plus que son absence. Même traitement
 * pour {@code errorMessage}, pour la même raison qu'en liste.
 */
public record DocumentDetailView(
        UUID id,
        String filename,
        DocumentFormat format,
        DocumentType type,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        long sizeBytes,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) TextExtractionView extraction) {

    /** {@code extraction} vaut {@code null} quand il n'y a rien à montrer. */
    public static DocumentDetailView of(Document document, TextExtractionView extraction) {
        return new DocumentDetailView(
                document.getId(),
                document.getFilename(),
                document.getFormat(),
                document.getFormat().type(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getSizeBytes(),
                document.getCreatedAt(),
                extraction);
    }
}
