package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentContentReplaced;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDriveContentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class ImportDriveFileHandler implements CommandHandler<ImportDriveFile> {

    private final DocumentRepository documentRepository;
    private final DocumentStorage documentStorage;
    private final TextExtractionRepository textExtractionRepository;
    private final TextChunkRepository textChunkRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public ImportDriveFileHandler(
            DocumentRepository documentRepository,
            DocumentStorage documentStorage,
            TextExtractionRepository textExtractionRepository,
            TextChunkRepository textChunkRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.documentRepository = documentRepository;
        this.documentStorage = documentStorage;
        this.textExtractionRepository = textExtractionRepository;
        this.textChunkRepository = textChunkRepository;
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
            handOver(alreadyImported.get(), command);
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
     * A second import of the same file announces nothing as long as Drive says it has not moved:
     * only the folder it now comes through is written, which a folder unwatched then watched
     * again renames.
     */
    private void handOver(Document document, ImportDriveFile command) {
        if (!command.watchedFolderId().equals(document.getWatchedFolderId())) {
            document.cameThrough(command.watchedFolderId());
            documentRepository.save(document);
        }
        if (hasMovedSinceTheImport(document, command.file())) {
            reingest(document, command);
        }
    }

    /**
     * Never the checksum: a Google Doc is exported into an archive rebuilt on every call, so two
     * exports of an untouched document differ, and comparing them would re-ingest the whole Drive
     * at every import. A time the base does not hold — Drive told none, or told one nothing could
     * read — is a move too: read as an immobility, it would freeze that document for good.
     */
    private static boolean hasMovedSinceTheImport(Document document, DriveFile file) {
        if (file.modifiedTime() == null) {
            return false;
        }
        Instant imported =
                document.getDriveProvenance().map(DriveProvenance::modifiedTime).orElse(null);
        return imported == null || file.modifiedTime().isAfter(imported);
    }

    /** Exactly what {@code ReplaceDocumentContent} does of an upload, from the Drive file instead. */
    private void reingest(Document document, ImportDriveFile command) {
        DriveFile file = command.file();
        Checksum checksum = Checksum.of(command.content());
        if (checksum.equals(document.getChecksum())) {
            // Above the return: left below it, the time of a binary whose content never moves
            // would never be written, and the column would drift from Drive for good.
            document.movedAt(file.modifiedTime());
            documentRepository.save(document);
            return;
        }

        document.reimported(
                file.name(),
                file.format(),
                checksum,
                command.content().length,
                file.provenance(),
                command.watchedFolderId());
        documentRepository.save(document);

        textExtractionRepository.deleteByDocumentId(document.getId());
        textChunkRepository.deleteByDocumentId(document.getId());

        // The file after the row: see ADR-0020.
        documentStorage.replace(document.getId(), command.content());

        domainEventPublisher.publish(
                new DocumentContentReplaced(document.getId(), document.getOwnerId(), clock.instant()));
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
