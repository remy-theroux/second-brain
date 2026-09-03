package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Orchestre le dépôt : reconnaissance du format, calcul de l'empreinte, refus du doublon,
 * écriture, conservation du fichier d'origine, puis annonce.
 *
 * <p>Aucun {@code @Transactional} ici — la transaction est ouverte par
 * {@code SpringCommandBus.dispatch} et couvre tout ce qui suit.
 *
 * <p><strong>L'ordre des étapes est un choix.</strong> Le contrôle du doublon en mémoire
 * d'abord, parce qu'il rend un refus <em>désignant</em> le document existant, ce que la
 * violation de contrainte ne saurait pas faire. L'écriture en base ensuite, avec son flush,
 * parce que c'est elle qui tranche en cas de dépôts simultanés. L'original après, parce que
 * sa conservation ne participe à aucune transaction — c'était vrai du système de fichiers,
 * ça l'est du stockage objet qui l'a remplacé, et le handler n'a pas à savoir lequel des
 * deux est derrière le port : écrit avant, l'original survivrait à un rollback en désignant
 * une ligne qui n'existe pas.
 *
 * <p>L'annonce en tout dernier. Elle ne prend effet qu'au commit — le port la diffère —,
 * donc sa place dans la séquence n'a aucune importance transactionnelle : elle est dernière
 * pour se lire comme ce qu'elle est, une annonce de ce qui vient d'être fait. Un dépôt
 * refusé ou annulé n'annonce rien.
 */
@Component
public class UploadDocumentHandler implements CommandHandler<UploadDocument> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public UploadDocumentHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Override
    public void handle(UploadDocument command) {
        DocumentFormat format = DocumentFormat.fromFilename(command.filename());

        Checksum checksum = Checksum.of(command.content());

        Optional<Document> existant = documentRepository.findByOwnerIdAndChecksum(command.ownerId(), checksum);
        if (existant.isPresent()) {
            throw new DuplicateDocumentException(existant.get().getId());
        }

        Document document = documentRepository.save(
                Document.upload(command.ownerId(), command.filename(), format, checksum, command.content().length));

        // Le fichier après la ligne : voir ADR-0020.
        documentStorage.store(document.getId(), command.content());

        domainEventPublisher.publish(new DocumentUploaded(document.getId(), document.getOwnerId(), clock.instant()));
    }
}
