package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnknownDriveChannelException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class NotifyDriveChangeHandler implements CommandHandler<NotifyDriveChange> {

    /** Sent once when a channel opens, to validate it. It announces no change whatsoever. */
    static final String SYNC_STATE = "sync";

    private final DriveChannelRepository driveChannelRepository;
    private final DriveConnectionRepository driveConnectionRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public NotifyDriveChangeHandler(
            DriveChannelRepository driveChannelRepository,
            DriveConnectionRepository driveConnectionRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.driveChannelRepository = driveChannelRepository;
        this.driveConnectionRepository = driveConnectionRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * It publishes exactly what the scheduled round publishes, and nothing else: a notification
     * decides of nothing, it says that something moved and that the Drive is worth reading.
     */
    @Override
    public void handle(NotifyDriveChange command) {
        DriveChannel channel = driveChannelRepository
                .findByChannelId(identifierOf(command))
                .orElseThrow(UnknownDriveChannelException::new);
        if (!channel.accepts(tokenOf(command), clock.instant())) {
            throw new UnknownDriveChannelException();
        }
        DriveConnection connection = driveConnectionRepository
                .findById(channel.getConnectionId())
                .orElseThrow(UnknownDriveChannelException::new);

        // Nothing can be read from this Drive any more, and nothing can stop the channel either:
        // the access that would take is the very access that was withdrawn. Dropping the row is
        // what makes the next notification answer 404, which is what makes Google unsubscribe.
        if (connection.getStatus() != DriveConnectionStatus.ACTIVE) {
            driveChannelRepository.delete(channel);
            return;
        }
        if (SYNC_STATE.equalsIgnoreCase(trimmed(command.resourceState()))) {
            return;
        }
        domainEventPublisher.publish(new DriveSynchronisationRequested(connection.getOwnerId(), clock.instant()));
    }

    private static String identifierOf(NotifyDriveChange command) {
        String channelId = trimmed(command.channelId());
        if (channelId.isEmpty()) {
            throw new UnknownDriveChannelException();
        }
        return channelId;
    }

    /** A token that no channel could ever carry is a token that matches none: same refusal, same 404. */
    private static DriveChannelToken tokenOf(NotifyDriveChange command) {
        try {
            return new DriveChannelToken(command.channelToken());
        } catch (IllegalArgumentException malformed) {
            throw new UnknownDriveChannelException();
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
