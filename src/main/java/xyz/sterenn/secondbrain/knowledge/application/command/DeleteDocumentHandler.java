package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class DeleteDocumentHandler implements CommandHandler<DeleteDocument> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;

    public DeleteDocumentHandler(DocumentRepository documentRepository, DocumentStorage documentStorage) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
    }

    @Override
    public void handle(DeleteDocument command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        documentRepository.delete(document);
        // La ligne avant le fichier : voir ADR-0020.
        documentStorage.delete(document.getId());
    }
}
