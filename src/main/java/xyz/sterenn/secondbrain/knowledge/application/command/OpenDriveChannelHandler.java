package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class OpenDriveChannelHandler implements CommandHandler<OpenDriveChannel> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final DriveChannels driveChannels;

    public OpenDriveChannelHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            DriveChannels driveChannels) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.driveChannels = driveChannels;
    }

    /**
     * Without a public HTTPS address Google can reach, no channel is opened at all — that is the
     * normal case in development, and the periodic scan keeps the base honest without it.
     */
    @Override
    public void handle(OpenDriveChannel command) {
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(DriveConnectionNotFoundException::new);

        driveChannels.open(connection).ifPresent(driveChannelRepository::save);
    }
}
