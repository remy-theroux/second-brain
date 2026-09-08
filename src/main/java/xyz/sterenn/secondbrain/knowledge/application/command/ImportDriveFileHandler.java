package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDriveContentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class ImportDriveFileHandler implements CommandHandler<ImportDriveFile> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ImportDriveFileHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * Three cases: a file already imported, a content the base already holds, and a new document.
     * Only the last one announces — the other two would send a content that has not moved
     * through the whole pipeline again.
     */
    @Override
    public void handle(ImportDriveFile command) {
        DriveFile file = command.file();
        Optional<Document> alreadyImported =
                documentRepository.findByOwnerIdAndDriveFileId(command.ownerId(), file.id());
        if (alreadyImported.isPresent()) {
            handOver(alreadyImported.get(), command.watchedFolderId());
            return;
        }

        Checksum checksum = Checksum.of(command.content());
        Optional<Document> sameContent = documentRepository.findByOwnerIdAndChecksum(command.ownerId(), checksum);
        if (sameContent.isPresent()) {
            attach(sameContent.get(), file, command.watchedFolderId());
            return;
        }

        Document document = documentRepository.save(Document.importedFromDrive(
                command.ownerId(),
                file.name(),
                file.format(),
                checksum,
                command.content().length,
                file.provenance(),
                command.watchedFolderId()));

        // The file after the row: see ADR-0020.
        documentStorage.store(document.getId(), command.content());

        domainEventPublisher.publish(new DocumentUploaded(document.getId(), document.getOwnerId(), clock.instant()));
    }

    /**
     * The only writing a second import does, and it announces nothing: the content has not moved,
     * only the folder it now comes through, which a folder unwatched then watched again renames.
     */
    private void handOver(Document document, UUID watchedFolderId) {
        if (watchedFolderId.equals(document.getWatchedFolderId())) {
            return;
        }
        document.cameThrough(watchedFolderId);
        documentRepository.save(document);
    }

    /**
     * Two Drive files can hold the same bytes, and the document keeps the first origin. The second
     * file has to be refused out loud: dropped in silence, it would be missing from the base, from
     * the rejections and from every later import, with nothing to explain the count.
     */
    private void attach(Document document, DriveFile file, UUID watchedFolderId) {
        if (document.getDriveProvenance().isPresent()) {
            throw new DuplicateDriveContentException();
        }
        document.attachTo(file.provenance(), watchedFolderId);
        documentRepository.save(document);
    }
}
