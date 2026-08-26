package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

/**
 * Retire un document et son fichier d'origine.
 *
 * <p>La ligne part avant le fichier : l'inverse laisserait, le temps d'un échec, une
 * entrée de liste dont l'original a déjà disparu. Le fichier en dernier fait courir le
 * risque symétrique — un rollback après l'effacement laisserait une ligne sans original —
 * mais celui-là ne se produit qu'en cas de panne, là où le premier se produirait à chaque
 * suppression échouée.
 *
 * <p>L'extraction n'est pas mentionnée ici et ne le sera pas : le {@code ON DELETE CASCADE}
 * de {@code knowledge_text_extractions} vers {@code knowledge_documents} l'emporte avec le
 * document, et celui de {@code knowledge_text_blocks} vers l'extraction emporte les blocs.
 * Cette méthode n'a pas eu à changer quand la table est arrivée, et n'aura pas à changer
 * quand une deuxième typologie ajoutera les siennes — à condition qu'elles cascadent aussi
 * (ADR-0030).
 */
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
        documentStorage.delete(document.getId());
    }
}
