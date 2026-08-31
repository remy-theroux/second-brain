package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

/**
 * Pose {@code FAILED} et son motif. Rien d'autre : pas d'événement, pas de nettoyage.
 *
 * <p>Le texte partiel n'a pas à être effacé — l'extraction est tout ou rien, et sa
 * transaction annulée n'a rien laissé derrière elle.
 */
@Component
public class MarkDocumentExtractionFailedHandler implements CommandHandler<MarkDocumentExtractionFailed> {

    private final DocumentRepository documentRepository;

    public MarkDocumentExtractionFailedHandler(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public void handle(MarkDocumentExtractionFailed command) {
        Document document = documentRepository
                .findByIdAndOwnerId(command.documentId(), command.ownerId())
                .orElseThrow(DocumentNotFoundException::new);

        document.markExtractionFailed(command.reason());
        documentRepository.save(document);
    }
}
