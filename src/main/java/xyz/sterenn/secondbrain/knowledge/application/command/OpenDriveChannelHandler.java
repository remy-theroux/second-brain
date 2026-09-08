package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveAccess;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class OpenDriveChannelHandler implements CommandHandler<OpenDriveChannel> {

    private static final Logger LOG = LoggerFactory.getLogger(OpenDriveChannelHandler.class);

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final GoogleDriveChannels googleDriveChannels;
    private final GoogleDriveChanges googleDriveChanges;
    private final DriveAccess driveAccess;
    private final Clock clock;
    private final String webhookUrl;

    public OpenDriveChannelHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            GoogleDriveChannels googleDriveChannels,
            GoogleDriveChanges googleDriveChanges,
            DriveAccess driveAccess,
            Clock clock,
            @Value("${secondbrain.drive.webhook-url:}") String webhookUrl) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.googleDriveChannels = googleDriveChannels;
        this.googleDriveChanges = googleDriveChanges;
        this.driveAccess = driveAccess;
        this.clock = clock;
        this.webhookUrl = webhookUrl;
    }

    /**
     * Without a public HTTPS address Google can reach, no channel is opened at all — that is the
     * normal case in development, and the periodic scan keeps the base honest without it.
     */
    @Override
    public void handle(OpenDriveChannel command) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOG.info("No public webhook address is configured: the push notifications stay off");
            return;
        }
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(DriveConnectionNotFoundException::new);

        DriveChannel channel = DriveChannel.open(connection.getId(), clock.instant());
        // The position only tells Drive where the feed it watches starts. We never read what a
        // notification carries, so which position it is has no bearing on what we then do.
        channel.subscribed(driveAccess.call(
                connection,
                accessToken -> googleDriveChannels.watch(
                        accessToken,
                        channel.getChannelId(),
                        channel.getToken(),
                        webhookUrl,
                        connection
                                .getChangesPageToken()
                                .orElseGet(() -> googleDriveChanges.startPageToken(accessToken)))));
        driveChannelRepository.save(channel);
    }
}
