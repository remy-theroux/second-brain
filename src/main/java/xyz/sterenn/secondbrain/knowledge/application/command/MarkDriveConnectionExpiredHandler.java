package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class MarkDriveConnectionExpiredHandler implements CommandHandler<MarkDriveConnectionExpired> {

    private final DriveConnectionRepository driveConnectionRepository;

    public MarkDriveConnectionExpiredHandler(DriveConnectionRepository driveConnectionRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
    }

    /** A repair, not a refusal: a connection disconnected since the revocation has nothing left to mark. */
    @Override
    public void handle(MarkDriveConnectionExpired command) {
        driveConnectionRepository.findByOwnerId(command.ownerId()).ifPresent(connection -> {
            connection.markNeedsReconnection();
            driveConnectionRepository.save(connection);
        });
    }
}
