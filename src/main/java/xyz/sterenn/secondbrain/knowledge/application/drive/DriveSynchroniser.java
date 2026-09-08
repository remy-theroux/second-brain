package xyz.sterenn.secondbrain.knowledge.application.drive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.DeleteDocument;
import xyz.sterenn.secondbrain.knowledge.application.command.ImportDriveFile;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDriveConnectionExpired;
import xyz.sterenn.secondbrain.knowledge.application.command.RecordDriveChangePosition;
import xyz.sterenn.secondbrain.knowledge.application.command.RenameDocument;
import xyz.sterenn.secondbrain.knowledge.domain.ImportPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveChangeTokenExpiredException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChangePage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolderChain;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * The mirror: what a watched Drive holds and what the base holds are the same thing. Neither a
 * command nor a query, and the listener calls it directly, for the reason
 * {@link DriveFolderImporter} carries — a round can last minutes, and one transaction for all of
 * it would hold a PostgreSQL connection throughout. One command per gesture instead.
 */
@Component
public class DriveSynchroniser {

    private static final Logger LOG = LoggerFactory.getLogger(DriveSynchroniser.class);

    private final CommandBus commandBus;
    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;
    private final DocumentRepository documentRepository;
    private final DriveAccess driveAccess;
    private final GoogleDriveChanges googleDriveChanges;
    private final GoogleDriveFolders googleDriveFolders;
    private final GoogleDriveFiles googleDriveFiles;
    private final DriveFolderImporter driveFolderImporter;

    public DriveSynchroniser(
            CommandBus commandBus,
            DriveConnectionRepository driveConnectionRepository,
            WatchedFolderRepository watchedFolderRepository,
            DocumentRepository documentRepository,
            DriveAccess driveAccess,
            GoogleDriveChanges googleDriveChanges,
            GoogleDriveFolders googleDriveFolders,
            GoogleDriveFiles googleDriveFiles,
            DriveFolderImporter driveFolderImporter) {
        this.commandBus = commandBus;
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
        this.documentRepository = documentRepository;
        this.driveAccess = driveAccess;
        this.googleDriveChanges = googleDriveChanges;
        this.googleDriveFolders = googleDriveFolders;
        this.googleDriveFiles = googleDriveFiles;
        this.driveFolderImporter = driveFolderImporter;
    }

    /**
     * A round that fails erases nothing and keeps nothing: it says so, and the next one replays
     * from the same token. Every destructive gesture rests on a complete read.
     */
    public void synchronise(UUID ownerId) {
        try {
            DriveConnection connection =
                    driveConnectionRepository.findByOwnerId(ownerId).orElseThrow(DriveNotConnectedException::new);
            run(connection);
        } catch (DriveAuthorizationRevokedException revoked) {
            LOG.error("Synchronisation of the Drive of {} met a withdrawn authorization", ownerId, revoked);
            // A second transaction, the round having rolled its own back: see ADR-0028.
            commandBus.dispatch(new MarkDriveConnectionExpired(ownerId));
        } catch (RuntimeException failure) {
            LOG.error("Synchronisation of the Drive of {} failed and changed nothing", ownerId, failure);
        }
    }

    private void run(DriveConnection connection) {
        Optional<String> kept = connection.getChangesPageToken();
        if (kept.isEmpty()) {
            fullScan(connection);
            return;
        }
        DriveChangePage page;
        try {
            page = driveAccess.call(
                    connection, accessToken -> googleDriveChanges.changesSince(accessToken, kept.get()));
        } catch (DriveChangeTokenExpiredException expired) {
            LOG.warn("Google no longer knows the change token of the Drive of {}", connection.getOwnerId());
            commandBus.dispatch(RecordDriveChangePosition.lost(connection.getOwnerId()));
            fullScan(connection);
            return;
        }
        apply(connection, page.changes());
        keep(connection.getOwnerId(), page.newStartPageToken());
    }

    /**
     * What is owed to a position nobody knows any more. The starting point is taken
     * <strong>before</strong> the scan: taken after it, everything that moved while it ran would
     * fall between two rounds and never be seen again.
     */
    private void fullScan(DriveConnection connection) {
        String startPageToken = driveAccess.call(connection, googleDriveChanges::startPageToken);
        List<WatchedFolder> watchedFolders = watchedFolderRepository.findAllByConnectionId(connection.getId());
        LOG.info(
                "Scanning the {} watched folders of the Drive of {} in full",
                watchedFolders.size(),
                connection.getOwnerId());
        for (WatchedFolder watchedFolder : watchedFolders) {
            driveFolderImporter.importFolder(connection.getOwnerId(), watchedFolder.getId());
        }
        keep(connection.getOwnerId(), startPageToken);
    }

    private void apply(DriveConnection connection, List<DriveChange> changes) {
        Map<String, UUID> watchedFolders = watchedFoldersOf(connection);
        Map<String, Optional<UUID>> alreadyResolved = new HashMap<>();
        LOG.info("Reflecting {} changes of the Drive of {}", changes.size(), connection.getOwnerId());
        for (DriveChange change : changes) {
            applyOne(connection, watchedFolders, alreadyResolved, change);
        }
    }

    /**
     * The resolution is outside the catch below, and that is the whole point: a Drive that falls
     * over while the parents of a file are read must stop the round, never be read as a file that
     * left the watched folders — that reading would erase the base on a hiccup.
     */
    private void applyOne(
            DriveConnection connection,
            Map<String, UUID> watchedFolders,
            Map<String, Optional<UUID>> alreadyResolved,
            DriveChange change) {
        UUID ownerId = connection.getOwnerId();
        Optional<Document> known = documentRepository.findByOwnerIdAndDriveFileId(ownerId, change.fileId());
        Optional<UUID> watchedFolder = change.isGone()
                ? Optional.empty()
                : watchedFolderOf(connection, watchedFolders, alreadyResolved, change);
        DriveChangeDecision decision = DriveChangeDecision.decide(change, known, watchedFolder);

        try {
            carryOut(connection, decision, watchedFolder);
        } catch (GoogleDriveUnavailableException | DriveAuthorizationRevokedException outage) {
            throw outage;
        } catch (RuntimeException leftOut) {
            LOG.warn("The change on the Drive file {} was left out", change.fileId(), leftOut);
        }
    }

    private void carryOut(DriveConnection connection, DriveChangeDecision decision, Optional<UUID> watchedFolder) {
        UUID ownerId = connection.getOwnerId();
        switch (decision) {
            case DriveChangeDecision.Import imported -> ingest(connection, imported.file(), imported.watchedFolderId());
            case DriveChangeDecision.Reingest reingest ->
                ingest(connection, reingest.file(), watchedFolder.orElseThrow());
            case DriveChangeDecision.Rename rename ->
                commandBus.dispatch(
                        new RenameDocument(ownerId, rename.document().getId(), rename.filename()));
            case DriveChangeDecision.Remove remove ->
                commandBus.dispatch(new DeleteDocument(remove.document().getId(), ownerId));
            case DriveChangeDecision.Nothing ignored -> {}
        }
    }

    /**
     * A re-ingestion goes through {@code ImportDriveFile} rather than {@code ReplaceDocumentContent}:
     * that one short-circuits on an identical checksum and writes nothing, so the modification time
     * of a binary whose timestamp alone moved would never be written and the file would be
     * downloaded again at every round, for ever.
     */
    private void ingest(DriveConnection connection, DriveFile file, UUID watchedFolderId) {
        if (ImportPolicy.isTooLarge(file.sizeBytes())) {
            LOG.warn("The Drive file {} is above the ceiling and was left out", file.id());
            return;
        }
        byte[] content = driveAccess.call(
                connection,
                accessToken -> file.isExported()
                        ? googleDriveFiles.export(accessToken, file.id())
                        : googleDriveFiles.download(accessToken, file.id()));
        commandBus.dispatch(new ImportDriveFile(connection.getOwnerId(), watchedFolderId, file, content));
    }

    private Map<String, UUID> watchedFoldersOf(DriveConnection connection) {
        Map<String, UUID> watchedFolders = new HashMap<>();
        watchedFolderRepository
                .findAllByConnectionId(connection.getId())
                .forEach(watchedFolder -> watchedFolders.put(watchedFolder.getDriveFolderId(), watchedFolder.getId()));
        return watchedFolders;
    }

    /**
     * The folders above the file are asked for only once each: a hundred changes in the same
     * folder would otherwise cost a hundred round trips to Drive.
     */
    private Optional<UUID> watchedFolderOf(
            DriveConnection connection,
            Map<String, UUID> watchedFolders,
            Map<String, Optional<UUID>> alreadyResolved,
            DriveChange change) {
        for (String parent : change.parents()) {
            UUID watchedFolder = watchedFolders.get(parent);
            if (watchedFolder != null) {
                return Optional.of(watchedFolder);
            }
        }
        for (String parent : change.parents()) {
            Optional<UUID> watchedFolder = alreadyResolved.computeIfAbsent(
                    parent, folderId -> watchedAncestorOf(connection, watchedFolders, folderId));
            if (watchedFolder.isPresent()) {
                return watchedFolder;
            }
        }
        return Optional.empty();
    }

    /**
     * "Climbed, and under no watched folder" is a removal; "not climbed" is a round that stops.
     * Confusing the two erases the document, its text, its chunks and its original on a parent
     * Drive hands back no more — and nothing brings those back.
     */
    private Optional<UUID> watchedAncestorOf(
            DriveConnection connection, Map<String, UUID> watchedFolders, String folderId) {
        DriveFolderChain chain =
                driveAccess.call(connection, accessToken -> googleDriveFolders.ancestors(accessToken, folderId));
        Optional<UUID> watchedFolder = chain.folders().stream()
                .map(watchedFolders::get)
                .filter(Objects::nonNull)
                .findFirst();
        if (watchedFolder.isEmpty() && !chain.reachedTheTop()) {
            LOG.error("The folders above the Drive folder {} could not be climbed: the round stops", folderId);
            throw new GoogleDriveUnavailableException();
        }
        return watchedFolder;
    }

    private void keep(UUID ownerId, String newStartPageToken) {
        if (newStartPageToken == null || newStartPageToken.isBlank()) {
            LOG.error("Google closed a change run without a new starting token: the previous one is kept");
            return;
        }
        commandBus.dispatch(new RecordDriveChangePosition(ownerId, newStartPageToken));
    }
}
