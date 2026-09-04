package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class MarkDocumentProcessingFailedHandler implements CommandHandler<MarkDocumentProcessingFailed> {

    private final DocumentRepository documentRepository;

    public MarkDocumentProcessingFailedHandler(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public void handle(MarkDocumentProcessingFailed command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        document.markProcessingFailed(command.reason());
        documentRepository.save(document);
    }
}
