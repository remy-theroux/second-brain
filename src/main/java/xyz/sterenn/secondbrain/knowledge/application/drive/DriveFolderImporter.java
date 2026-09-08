package xyz.sterenn.secondbrain.knowledge.application.drive;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.ImportDriveFile;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDriveConnectionExpired;
import xyz.sterenn.secondbrain.knowledge.application.command.RecordDriveImportOutcome;
import xyz.sterenn.secondbrain.knowledge.domain.ImportPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveImportRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.WatchedFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFiles;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportRejection;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * Neither a command nor a query, and the listener calls it directly: a folder of five hundred
 * files would hold the transaction a bus opens — hence a PostgreSQL connection — for the whole
 * import, and a Drive falling over halfway would take back everything already written. One
 * command per file instead, so one short transaction each.
 */
@Component
public class DriveFolderImporter {

    private static final Logger LOG = LoggerFactory.getLogger(DriveFolderImporter.class);

    private static final String UNEXPECTED_FAILURE = "L'import de ce dossier a échoué de façon inattendue.";

    private static final String FILE_FAILURE = "Ce fichier n'a pas pu être importé.";

    private final CommandBus commandBus;
    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;
    private final DriveAccess driveAccess;
    private final GoogleDriveFiles googleDriveFiles;
    private final Clock clock;

    public DriveFolderImporter(
            CommandBus commandBus,
            DriveConnectionRepository driveConnectionRepository,
            WatchedFolderRepository watchedFolderRepository,
            DriveAccess driveAccess,
            GoogleDriveFiles googleDriveFiles,
            Clock clock) {
        this.commandBus = commandBus;
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
        this.driveAccess = driveAccess;
        this.googleDriveFiles = googleDriveFiles;
        this.clock = clock;
    }

    public void importFolder(UUID ownerId, UUID watchedFolderId) {
        try {
            walkAndRecord(ownerId, watchedFolderId);
        } catch (RuntimeException unrecorded) {
            // Nothing can be written onto a folder or a connection that has gone between the
            // request and the walk; what would otherwise be missing is the trace, the message
            // being rejected without requeue and the previous outcome staying on screen.
            LOG.error("Import of the watched folder {} left no outcome behind", watchedFolderId, unrecorded);
            throw unrecorded;
        }
    }

    private void walkAndRecord(UUID ownerId, UUID watchedFolderId) {
        DriveConnection connection =
                driveConnectionRepository.findByOwnerId(ownerId).orElseThrow(DriveNotConnectedException::new);
        WatchedFolder watchedFolder = watchedFolderRepository
                .findByIdAndConnectionId(watchedFolderId, connection.getId())
                .orElseThrow(WatchedFolderNotFoundException::new);

        List<DriveImportRejection> rejections = new ArrayList<>();
        try {
            walk(connection, watchedFolder, rejections);
        } catch (DriveAuthorizationRevokedException revoked) {
            LOG.error("Import of the watched folder {} met a withdrawn authorization", watchedFolderId, revoked);
            // Two second transactions, the walk having rolled its own back: see ADR-0028. The
            // outcome alone would leave the owner with a failed import and no idea to reconnect.
            commandBus.dispatch(new MarkDriveConnectionExpired(ownerId));
            record(ownerId, watchedFolderId, DriveImportStatus.FAILED, revoked.getMessage(), rejections);
            return;
        } catch (RuntimeException failure) {
            LOG.error("Import of the watched folder {} failed", watchedFolderId, failure);
            // A second transaction, the walk having rolled its own back: see ADR-0028.
            record(ownerId, watchedFolderId, DriveImportStatus.FAILED, reason(failure), rejections);
            return;
        }
        record(ownerId, watchedFolderId, DriveImportStatus.SUCCEEDED, null, rejections);
    }

    /**
     * The ceiling is judged on what the listing says, before the download: paying the transfer of
     * a file about to be refused buys nothing.
     */
    private void walk(DriveConnection connection, WatchedFolder watchedFolder, List<DriveImportRejection> rejections) {
        List<DriveFile> files = driveAccess.call(
                connection, accessToken -> googleDriveFiles.filesUnder(accessToken, watchedFolder.getDriveFolderId()));
        LOG.info("Importing {} readable files of the watched folder {}", files.size(), watchedFolder.getId());

        for (DriveFile file : files) {
            if (ImportPolicy.isTooLarge(file.sizeBytes())) {
                rejections.add(DriveImportRejection.of(file, ImportPolicy.tooLargeReason()));
                continue;
            }
            importOne(connection, watchedFolder, file, rejections);
        }
    }

    /**
     * Only a Drive that is really down and a withdrawn authorization stop the walk: everything
     * else — a file gone since the listing, a hiccup of the object storage, a row written under
     * this one — leaves that one file out and lets the next five hundred in.
     */
    private void importOne(
            DriveConnection connection,
            WatchedFolder watchedFolder,
            DriveFile file,
            List<DriveImportRejection> rejections) {
        try {
            byte[] content =
                    driveAccess.call(connection, accessToken -> googleDriveFiles.download(accessToken, file.id()));
            commandBus.dispatch(new ImportDriveFile(connection.getOwnerId(), watchedFolder.getId(), file, content));
        } catch (GoogleDriveUnavailableException | DriveAuthorizationRevokedException outage) {
            throw outage;
        } catch (RuntimeException leftOut) {
            LOG.warn("The file {} of the watched folder {} was left out", file.id(), watchedFolder.getId(), leftOut);
            rejections.add(DriveImportRejection.of(file, rejectionReason(leftOut)));
        }
    }

    private void record(
            UUID ownerId,
            UUID watchedFolderId,
            DriveImportStatus status,
            String error,
            List<DriveImportRejection> rejections) {
        commandBus.dispatch(new RecordDriveImportOutcome(ownerId, watchedFolderId, status, error, rejections));
    }

    private static String reason(RuntimeException failure) {
        return failure instanceof GoogleDriveUnavailableException || failure instanceof DriveImportRejectedException
                ? failure.getMessage()
                : UNEXPECTED_FAILURE;
    }

    private static String rejectionReason(RuntimeException failure) {
        return failure instanceof DriveImportRejectedException refusal ? refusal.getMessage() : FILE_FAILURE;
    }
}
