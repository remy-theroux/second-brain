package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

/**
 * Orchestre le dépôt : reconnaissance du format, calcul de l'empreinte, refus du doublon,
 * écriture, puis conservation du fichier d'origine.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit.
 *
 * <p><strong>L'ordre des trois dernières étapes est un choix.</strong> Le contrôle du
 * doublon en mémoire d'abord, parce qu'il rend un refus <em>désignant</em> le document
 * existant, ce que la violation de contrainte ne saurait pas faire. L'écriture en base
 * ensuite, avec son flush, parce que c'est elle qui tranche en cas de dépôts simultanés.
 * Le fichier en dernier, parce qu'un système de fichiers ne participe à aucune
 * transaction : écrit avant, il survivrait à un rollback en désignant une ligne qui
 * n'existe pas.
 */
@Component
public class UploadDocumentHandler implements CommandHandler<UploadDocument> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;

    public UploadDocumentHandler(DocumentRepository documentRepository, DocumentStorage documentStorage) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
    }

    @Override
    public void handle(UploadDocument command) {
        // Lève UnsupportedDocumentFormatException, message énonçant les formats acceptés.
        DocumentFormat format = DocumentFormat.fromFilename(command.filename());

        // C'est le contenu qui fait foi : le même fichier renommé reste le même document.
        Checksum checksum = Checksum.of(command.content());

        Optional<Document> existant = documentRepository.findByOwnerIdAndChecksum(command.ownerId(), checksum);
        if (existant.isPresent()) {
            throw new DuplicateDocumentException(existant.get().getId());
        }

        Document document = documentRepository.save(
                Document.upload(command.ownerId(), command.filename(), format, checksum, command.content().length));

        documentStorage.store(document.getId(), command.content());
    }
}
