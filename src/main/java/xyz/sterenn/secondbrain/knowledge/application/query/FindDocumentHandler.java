package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

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
