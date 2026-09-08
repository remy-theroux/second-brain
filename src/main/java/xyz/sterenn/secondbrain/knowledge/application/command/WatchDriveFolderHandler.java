package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveAccess;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.FolderAlreadyCoveredException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class WatchDriveFolderHandler implements CommandHandler<WatchDriveFolder> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;
    private final DriveAccess driveAccess;
    private final GoogleDriveFolders googleDriveFolders;
    private final Clock clock;

    public WatchDriveFolderHandler(
            DriveConnectionRepository driveConnectionRepository,
            WatchedFolderRepository watchedFolderRepository,
            DriveAccess driveAccess,
            GoogleDriveFolders googleDriveFolders,
            Clock clock) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
        this.driveAccess = driveAccess;
        this.googleDriveFolders = googleDriveFolders;
        this.clock = clock;
    }

    @Override
    public void handle(WatchDriveFolder command) {
        DriveConnection connection =
                driveConnectionRepository.findByOwnerId(command.ownerId()).orElseThrow(DriveNotConnectedException::new);

        DriveFolder folder = driveAccess
                .call(
                        connection,
                        accessToken -> googleDriveFolders.folder(
                                accessToken, command.folderId().trim()))
                .orElseThrow(DriveFolderNotFoundException::new);

        refuseIfAlreadyCovered(connection, folder);

        watchedFolderRepository.save(WatchedFolder.watch(connection.getId(), folder, clock.instant()));
    }

    /**
     * The candidate itself, then its ancestors nearest first: a folder inside a watched one
     * would have its files swept twice.
     */
    private void refuseIfAlreadyCovered(DriveConnection connection, DriveFolder folder) {
        Map<String, String> namesByDriveFolderId =
                watchedFolderRepository.findAllByConnectionId(connection.getId()).stream()
                        .collect(Collectors.toMap(
                                WatchedFolder::getDriveFolderId, WatchedFolder::getName, (first, second) -> first));
        if (namesByDriveFolderId.isEmpty()) {
            return;
        }

        refuse(namesByDriveFolderId.get(folder.id()));
        for (String ancestor : driveAccess
                .call(connection, accessToken -> googleDriveFolders.ancestors(accessToken, folder.id()))
                .folders()) {
            refuse(namesByDriveFolderId.get(ancestor));
        }
    }

    private static void refuse(String coveringFolderName) {
        if (coveringFolderName != null) {
            throw new FolderAlreadyCoveredException(coveringFolderName);
        }
    }
}
