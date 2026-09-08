package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveAccess;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class CloseDriveChannelHandler implements CommandHandler<CloseDriveChannel> {

    private static final Logger LOG = LoggerFactory.getLogger(CloseDriveChannelHandler.class);

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final GoogleDriveChannels googleDriveChannels;
    private final DriveAccess driveAccess;

    public CloseDriveChannelHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            GoogleDriveChannels googleDriveChannels,
            DriveAccess driveAccess) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.googleDriveChannels = googleDriveChannels;
        this.driveAccess = driveAccess;
    }

    /**
     * The row goes whether or not Google agrees to stop the channel: the access this would need
     * is exactly the access a withdrawn authorization no longer grants, and a channel Google
     * keeps alive expires on its own — every notification it sends meanwhile gets the 404 that
     * makes Google unsubscribe it.
     */
    @Override
    public void handle(CloseDriveChannel command) {
        Optional<DriveConnection> connection = driveConnectionRepository.findByOwnerId(command.ownerId());
        if (connection.isEmpty()) {
            return;
        }
        driveChannelRepository
                .findByConnectionId(connection.get().getId())
                .ifPresent(channel -> close(connection.get(), channel));
    }

    private void close(DriveConnection connection, DriveChannel channel) {
        try {
            driveAccess.call(connection, accessToken -> {
                googleDriveChannels.stop(accessToken, channel.getChannelId(), channel.getResourceId());
                return null;
            });
        } catch (RuntimeException failure) {
            LOG.warn(
                    "The channel {} could not be stopped at Google: it will expire on its own",
                    channel.getChannelId(),
                    failure);
        }
        driveChannelRepository.delete(channel);
    }
}
