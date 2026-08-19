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
 * <p>Les extraits ne sont pas mentionnés ici : la table n'existe pas encore. Le ticket qui
 * la créera posera un {@code ON DELETE CASCADE}, et cette méthode n'aura pas à changer.
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
