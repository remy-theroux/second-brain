package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveFolderImportRequested;
import xyz.sterenn.secondbrain.knowledge.domain.exception.WatchedFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class RequestDriveFolderImportHandler implements CommandHandler<RequestDriveFolderImport> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public RequestDriveFolderImportHandler(
            DriveConnectionRepository driveConnectionRepository,
            WatchedFolderRepository watchedFolderRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /** The folder is looked up before announcing: the worker would have nothing to walk otherwise. */
    @Override
    public void handle(RequestDriveFolderImport command) {
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(WatchedFolderNotFoundException::new);

        WatchedFolder watchedFolder = watchedFolderRepository
                .findByIdAndConnectionId(command.watchedFolderId(), connection.getId())
                .orElseThrow(WatchedFolderNotFoundException::new);

        domainEventPublisher.publish(
                new DriveFolderImportRequested(watchedFolder.getId(), command.ownerId(), clock.instant()));
    }
}
