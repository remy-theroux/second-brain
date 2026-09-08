package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class RequestDriveSynchronisationHandler implements CommandHandler<RequestDriveSynchronisation> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public RequestDriveSynchronisationHandler(
            DriveConnectionRepository driveConnectionRepository,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    /**
     * One announcement per connection rather than one for all of them: the listener consumes one
     * at a time, so a Drive falling over costs its owner a round, never everyone else's.
     */
    @Override
    public void handle(RequestDriveSynchronisation command) {
        for (DriveConnection connection : driveConnectionRepository.findAllActive()) {
            domainEventPublisher.publish(new DriveSynchronisationRequested(connection.getOwnerId(), clock.instant()));
        }
    }
}
