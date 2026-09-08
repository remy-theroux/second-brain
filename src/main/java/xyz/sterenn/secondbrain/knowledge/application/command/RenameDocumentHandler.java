package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class RenameDocumentHandler implements CommandHandler<RenameDocument> {

    private final DocumentRepository documentRepository;

    public RenameDocumentHandler(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /** No announcement: the content has not moved, so nothing downstream has anything to redo. */
    @Override
    public void handle(RenameDocument command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        document.rename(command.filename());
        documentRepository.save(document);
    }
}
