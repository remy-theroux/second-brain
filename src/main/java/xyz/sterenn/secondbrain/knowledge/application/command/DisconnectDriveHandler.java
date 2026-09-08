package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class DisconnectDriveHandler implements CommandHandler<DisconnectDrive> {

    private final DriveConnectionRepository driveConnectionRepository;

    public DisconnectDriveHandler(DriveConnectionRepository driveConnectionRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
    }

    /** The connection, and nothing else: the documents already imported stay. */
    @Override
    public void handle(DisconnectDrive command) {
        driveConnectionRepository.delete(driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(DriveConnectionNotFoundException::new));
    }
}
