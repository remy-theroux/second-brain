package xyz.sterenn.secondbrain.knowledge.application.drive;

import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.DriveChannelPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;

/**
 * The two gestures of a push subscription, shared by the handlers that open one, close one,
 * renew one, or leave with the Drive. Which of them is due stays a decision of its handler.
 */
@Component
public class DriveChannels {

    private static final Logger LOG = LoggerFactory.getLogger(DriveChannels.class);

    private final DriveChannelRepository driveChannelRepository;
    private final GoogleDriveChannels googleDriveChannels;
    private final GoogleDriveChanges googleDriveChanges;
    private final DriveAccess driveAccess;
    private final Clock clock;
    private final String webhookUrl;

    public DriveChannels(
            DriveChannelRepository driveChannelRepository,
            GoogleDriveChannels googleDriveChannels,
            GoogleDriveChanges googleDriveChanges,
            DriveAccess driveAccess,
            Clock clock,
            @Value("${secondbrain.drive.webhook-url}") String webhookUrl) {
        this.driveChannelRepository = driveChannelRepository;
        this.googleDriveChannels = googleDriveChannels;
        this.googleDriveChanges = googleDriveChanges;
        this.driveAccess = driveAccess;
        this.clock = clock;
        this.webhookUrl = webhookUrl;
    }

    /**
     * Subscribes at Google and hands the channel back <em>unsaved</em>: a renewal has an older
     * row to drop first, and {@code connection_id} is unique.
     *
     * <p>Empty when no public HTTPS address is configured, which is the normal case in
     * development: Google cannot reach a localhost, so no channel is opened at all.
     */
    public Optional<DriveChannel> open(DriveConnection connection) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            LOG.info("No public webhook address is configured: the push notifications stay off");
            return Optional.empty();
        }
        DriveChannel channel = DriveChannel.open(connection.getId(), clock.instant());
        // The position only tells Drive where the feed it watches starts, and a notification says
        // nothing of what it carries. Kept on the connection, it would count as a read position
        // and rob the first synchronisation round of the full scan it owes a new connection.
        channel.subscribed(driveAccess.call(
                connection,
                accessToken -> googleDriveChannels.watch(
                        accessToken,
                        channel.getChannelId(),
                        channel.getToken(),
                        webhookUrl,
                        connection
                                .getChangesPageToken()
                                .orElseGet(() -> googleDriveChanges.startPageToken(accessToken)),
                        DriveChannelPolicy.REQUESTED_LIFETIME)));
        return Optional.of(channel);
    }

    /**
     * The row goes whether or not Google agrees to stop the channel: the access this would need
     * is exactly the access a withdrawn authorization no longer grants, and a channel Google
     * keeps alive expires on its own — every notification it sends meanwhile gets the 404 that
     * makes Google unsubscribe it.
     */
    public void close(DriveConnection connection, DriveChannel channel) {
        try {
            driveAccess.call(connection, accessToken -> {
                googleDriveChannels.stop(accessToken, channel.getChannelId(), channel.getResourceId());
                return null;
            });
        } catch (RuntimeException failure) {
            LOG.warn(
                    "The channel {} could not be stopped at Google, it will expire on its own: {}",
                    channel.getChannelId(),
                    DriveFailures.describe(failure));
        }
        driveChannelRepository.delete(channel);
    }
}
