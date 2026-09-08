package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class RecordDriveChangePositionHandler implements CommandHandler<RecordDriveChangePosition> {

    private final DriveConnectionRepository driveConnectionRepository;

    public RecordDriveChangePositionHandler(DriveConnectionRepository driveConnectionRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
    }

    @Override
    public void handle(RecordDriveChangePosition command) {
        DriveConnection connection =
                driveConnectionRepository.findByOwnerId(command.ownerId()).orElseThrow(DriveNotConnectedException::new);

        if (command.pageToken() == null) {
            connection.forgetChangesPageToken();
        } else {
            connection.keepChangesPageToken(command.pageToken());
        }
        driveConnectionRepository.save(connection);
    }
}
