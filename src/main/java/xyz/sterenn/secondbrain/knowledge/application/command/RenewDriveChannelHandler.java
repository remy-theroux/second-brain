package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class RenewDriveChannelHandler implements CommandHandler<RenewDriveChannel> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveChannelRepository driveChannelRepository;
    private final DriveChannels driveChannels;
    private final Clock clock;

    public RenewDriveChannelHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveChannelRepository driveChannelRepository,
            DriveChannels driveChannels,
            Clock clock) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveChannelRepository = driveChannelRepository;
        this.driveChannels = driveChannels;
        this.clock = clock;
    }

    /**
     * The new channel is opened <em>before</em> the old one is closed: the other order leaves a
     * window with no subscription at all. Both live for the span of one call, so one change can
     * be notified twice — of no consequence, a notification only asks for a reading.
     */
    @Override
    public void handle(RenewDriveChannel command) {
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(command.ownerId())
                .orElseThrow(DriveConnectionNotFoundException::new);
        Optional<DriveChannel> current = driveChannelRepository.findByConnectionId(connection.getId());
        if (current.isPresent() && !current.get().needsRenewal(clock.instant())) {
            return;
        }
        Optional<DriveChannel> fresh = driveChannels.open(connection);
        if (fresh.isEmpty()) {
            return;
        }
        // Dropped before the new row is written, and the repository flushes: connection_id is
        // unique, and Hibernate orders its inserts before its deletes.
        current.ifPresent(previous -> driveChannels.close(connection, previous));
        driveChannelRepository.save(fresh.get());
    }
}
