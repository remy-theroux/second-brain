package xyz.sterenn.secondbrain.knowledge.application.drive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.RenewDriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/**
 * One round of the clock, and it is neither a command nor a query, for the reason
 * {@link DriveSynchroniser} carries: a round dispatched as one command would hold a single
 * transaction — hence one PostgreSQL connection — for up to three calls to Google per connected
 * Drive, and the catch below would swallow an exception that had already marked that shared
 * transaction rollback-only, dropping at commit the very rows the round had just written.
 */
@Component
public class DriveChannelRenewer {

    private static final Logger LOG = LoggerFactory.getLogger(DriveChannelRenewer.class);

    private final CommandBus commandBus;
    private final DriveConnectionRepository driveConnectionRepository;

    public DriveChannelRenewer(CommandBus commandBus, DriveConnectionRepository driveConnectionRepository) {
        this.commandBus = commandBus;
        this.driveConnectionRepository = driveConnectionRepository;
    }

    /**
     * A Drive that falls over costs its owner a renewal, never everyone else's: one command per
     * connection, hence one short transaction each, and a withdrawn authorization is named by the
     * synchronisation round, which is where a connection is marked as needing one.
     */
    public void renewAll() {
        for (DriveConnection connection : driveConnectionRepository.findAllActive()) {
            try {
                commandBus.dispatch(new RenewDriveChannel(connection.getOwnerId()));
            } catch (RuntimeException failure) {
                LOG.warn(
                        "The channel of the connection {} could not be renewed: {}",
                        connection.getId(),
                        DriveFailures.describe(failure));
            }
        }
    }
}
