package xyz.sterenn.secondbrain.knowledge.application.command;

import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class DisconnectDriveHandler implements CommandHandler<DisconnectDrive> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final DriveChannels driveChannels;
    private final GoogleAccessTokens googleAccessTokens;

    public DisconnectDriveHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            DriveChannels driveChannels,
            GoogleAccessTokens googleAccessTokens) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.driveChannels = driveChannels;
        this.googleAccessTokens = googleAccessTokens;
    }

    /** The connection, and nothing else: the documents already imported stay. */
    @Override
    public void handle(DisconnectDrive command) {
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(DriveConnectionNotFoundException::new);

        // Before the connection goes, because stopping the channel takes the access it carries:
        // left open, Google keeps notifying a Drive nobody reads any more until its own deadline.
        driveChannelRepository
                .findByConnectionId(connection.getId())
                .ifPresent(channel -> driveChannels.close(connection, channel));
        driveConnectionRepository.delete(connection);
        // The access token outlives its connection by up to an hour otherwise: it is kept in memory.
        googleAccessTokens.invalidate(connection);
    }
}
