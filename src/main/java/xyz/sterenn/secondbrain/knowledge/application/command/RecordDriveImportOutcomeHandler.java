package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.WatchedFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class RecordDriveImportOutcomeHandler implements CommandHandler<RecordDriveImportOutcome> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;
    private final Clock clock;

    public RecordDriveImportOutcomeHandler(
            DriveConnectionRepository driveConnectionRepository,
            WatchedFolderRepository watchedFolderRepository,
            Clock clock) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
        this.clock = clock;
    }

    @Override
    public void handle(RecordDriveImportOutcome command) {
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(WatchedFolderNotFoundException::new);

        WatchedFolder watchedFolder = watchedFolderRepository
                .findByIdAndConnectionId(command.watchedFolderId(), connection.getId())
                .orElseThrow(WatchedFolderNotFoundException::new);

        watchedFolder.recordImport(clock.instant(), command.status(), command.error(), command.rejections());
        watchedFolderRepository.save(watchedFolder);
    }
}
