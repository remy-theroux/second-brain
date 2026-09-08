package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.WatchedFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class UnwatchDriveFolderHandler implements CommandHandler<UnwatchDriveFolder> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;

    public UnwatchDriveFolderHandler(
            DriveConnectionRepository driveConnectionRepository, WatchedFolderRepository watchedFolderRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
    }

    /** The folder leaves the sources, and nothing else: neither the connection nor the documents move. */
    @Override
    public void handle(UnwatchDriveFolder command) {
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(WatchedFolderNotFoundException::new);

        watchedFolderRepository.delete(watchedFolderRepository
                .findByIdAndConnectionId(command.watchedFolderId(), connection.getId())
                .orElseThrow(WatchedFolderNotFoundException::new));
    }
}
