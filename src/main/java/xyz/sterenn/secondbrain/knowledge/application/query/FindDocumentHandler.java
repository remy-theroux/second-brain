package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

/**
 * Aucun {@code @Transactional} ici : {@code SpringQueryBus.ask} ouvre déjà une transaction en
 * lecture seule.
 *
 * <p><strong>C'est ici que la deuxième typologie se branchera.</strong> Le document est lu
 * d'abord, sa typologie ensuite, et c'est elle qui décide quel dépôt interroger — chaque
 * typologie a les siens (ADR-0030). Aujourd'hui il n'y en a qu'une, et le test de typologie
 * ci-dessous est le point d'accroche, pas une précaution inutile : sans lui, un format sonore
 * irait chercher son texte dans la table des extractions textuelles.
 */
@Component
public class FindDocumentHandler implements QueryHandler<FindDocument, Optional<DocumentDetailView>> {

    private final DocumentRepository documentRepository;
    private final TextExtractionRepository textExtractionRepository;

    public FindDocumentHandler(
            DocumentRepository documentRepository, TextExtractionRepository textExtractionRepository) {
        this.documentRepository = documentRepository;
        this.textExtractionRepository = textExtractionRepository;
    }

    @Override
    public Optional<DocumentDetailView> handle(FindDocument query) {
        return documentRepository
                .findByIdAndOwnerId(query.documentId(), query.ownerId())
                .map(document -> DocumentDetailView.of(document, extractionDe(document)));
    }

    /** {@code null} quand il n'y a rien à montrer : en attente, en échec, ou typologie non lue. */
    private TextExtractionView extractionDe(Document document) {
        if (document.getFormat().type() != DocumentType.TEXTUAL) {
            return null;
        }
        return textExtractionRepository
                .findByDocumentId(document.getId())
                .map(TextExtractionView::of)
                .orElse(null);
    }
}
