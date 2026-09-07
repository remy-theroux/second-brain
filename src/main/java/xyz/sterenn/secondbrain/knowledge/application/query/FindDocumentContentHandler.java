package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.MissingDocumentContentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class FindDocumentContentHandler implements QueryHandler<FindDocumentContent, Optional<DocumentContentView>> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;

    public FindDocumentContentHandler(DocumentRepository documentRepository, DocumentStorage documentStorage) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
    }

    // An empty Optional means "no such document for this owner". A row without its object is
    // not an absence but a broken invariant, so it throws — see the design, decision 6.
    @Override
    public Optional<DocumentContentView> handle(FindDocumentContent query) {
        return documentRepository
                .findByIdAndOwnerId(query.documentId(), query.ownerId())
                .map(document -> new DocumentContentView(
                        document.getFilename(),
                        document.getFormat(),
                        documentStorage.read(document.getId()).orElseThrow(MissingDocumentContentException::new)));
    }
}
