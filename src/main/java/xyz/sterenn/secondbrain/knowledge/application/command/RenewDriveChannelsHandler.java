package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class RenewDriveChannelsHandler implements CommandHandler<RenewDriveChannels> {

    private static final Logger LOG = LoggerFactory.getLogger(RenewDriveChannelsHandler.class);

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final DriveChannels driveChannels;
    private final Clock clock;

    public RenewDriveChannelsHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            DriveChannels driveChannels,
            Clock clock) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.driveChannels = driveChannels;
        this.clock = clock;
    }

    @Override
    public void handle(RenewDriveChannels command) {
        for (DriveConnection connection : driveConnectionRepository.findAllActive()) {
            Optional<DriveChannel> current = driveChannelRepository.findByConnectionId(connection.getId());
            if (current.isPresent() && !current.get().needsRenewal(clock.instant())) {
                continue;
            }
            renew(connection, current);
        }
    }

    /**
     * The new channel is opened <em>before</em> the old one is closed: the other order leaves a
     * window with no subscription at all. Both live for the span of one call, so one change can
     * be notified twice — of no consequence, a notification only asks for a reading.
     *
     * <p>A Drive that falls over costs its owner a renewal, never everyone else's: the round
     * carries on, and a withdrawn authorization is named by the synchronisation round, which is
     * where a connection is marked as needing one.
     */
    private void renew(DriveConnection connection, Optional<DriveChannel> current) {
        try {
            Optional<DriveChannel> fresh = driveChannels.open(connection);
            if (fresh.isEmpty()) {
                return;
            }
            // Dropped before the new row is written, and the repository flushes: connection_id is
            // unique, and Hibernate orders its inserts before its deletes.
            current.ifPresent(previous -> driveChannels.close(connection, previous));
            driveChannelRepository.save(fresh.get());
        } catch (RuntimeException failure) {
            LOG.warn("The channel of the connection {} could not be renewed", connection.getId(), failure);
        }
    }
}
