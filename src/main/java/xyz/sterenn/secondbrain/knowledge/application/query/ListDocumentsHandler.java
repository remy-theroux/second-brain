package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class ListDocumentsHandler implements QueryHandler<ListDocuments, List<DocumentView>> {

    private final DocumentRepository documentRepository;

    public ListDocumentsHandler(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public List<DocumentView> handle(ListDocuments query) {
        return documentRepository.findAllByOwnerId(query.ownerId()).stream()
                .map(DocumentView::of)
                .toList();
    }
}
