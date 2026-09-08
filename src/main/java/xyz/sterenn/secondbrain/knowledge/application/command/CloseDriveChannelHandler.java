package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class CloseDriveChannelHandler implements CommandHandler<CloseDriveChannel> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final DriveChannels driveChannels;

    public CloseDriveChannelHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            DriveChannels driveChannels) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.driveChannels = driveChannels;
    }

    @Override
    public void handle(CloseDriveChannel command) {
        Optional<DriveConnection> connection = driveConnectionRepository.findByOwnerId(command.ownerId());
        if (connection.isEmpty()) {
            return;
        }
        driveChannelRepository
                .findByConnectionId(connection.get().getId())
                .ifPresent(channel -> driveChannels.close(connection.get(), channel));
    }
}
