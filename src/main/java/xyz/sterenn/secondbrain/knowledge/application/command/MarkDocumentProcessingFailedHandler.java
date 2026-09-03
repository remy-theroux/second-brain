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
 * transaction annulée n'a rien laissé derrière elle. Pour un échec de vectorisation, c'est
 * l'inverse qui est vrai, et voulu : le texte extrait, lui, <strong>survit</strong>, parce que
 * cette transaction-ci n'y a pas touché — l'écran de détail montre donc ce qui a fonctionné et
 * où ça s'est arrêté (spec, décision 8).
 */
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
