package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveAuthorizationRequest;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveConnected;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidDriveAuthorizationException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveAuthorizationRequestRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveGrant;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

@Component
public class CompleteDriveAuthorizationHandler implements CommandHandler<CompleteDriveAuthorization> {

    private final DriveAuthorizationRequestRepository driveAuthorizationRequestRepository;
    private final DriveConnectionRepository driveConnectionRepository;
    private final GoogleDriveAuthorization googleDriveAuthorization;
    private final DomainEventPublisher domainEventPublisher;
    private final Clock clock;

    public CompleteDriveAuthorizationHandler(
            DriveAuthorizationRequestRepository driveAuthorizationRequestRepository,
            DriveConnectionRepository driveConnectionRepository,
            GoogleDriveAuthorization googleDriveAuthorization,
            DomainEventPublisher domainEventPublisher,
            Clock clock) {
        this.driveAuthorizationRequestRepository = driveAuthorizationRequestRepository;
        this.driveConnectionRepository = driveConnectionRepository;
        this.googleDriveAuthorization = googleDriveAuthorization;
        this.domainEventPublisher = domainEventPublisher;
        this.clock = clock;
    }

    @Override
    public void handle(CompleteDriveAuthorization command) {
        DriveAuthorizationState state = readState(command.state());
        Instant now = clock.instant();

        DriveAuthorizationRequest request = driveAuthorizationRequestRepository
                .findByState(state)
                .orElseThrow(InvalidDriveAuthorizationException::new);
        request.consume(state, now);
        driveAuthorizationRequestRepository.save(request);

        DriveGrant grant = googleDriveAuthorization.exchange(command.authorizationCode());

        driveConnectionRepository.save(driveConnectionRepository
                .findByOwnerId(request.getOwnerId())
                .map(connection -> {
                    connection.refresh(grant.googleEmail(), grant.refreshToken(), now);
                    return connection;
                })
                .orElseGet(() ->
                        DriveConnection.connect(request.getOwnerId(), grant.googleEmail(), grant.refreshToken(), now)));

        // Nothing else opens the first channel of a connection but the hourly round: without this,
        // a Drive just connected has no subscription at all for a whole interval — the very hour
        // its owner drops files in it and watches the screen.
        domainEventPublisher.publish(new DriveConnected(request.getOwnerId(), now));
    }

    private DriveAuthorizationState readState(String state) {
        try {
            return new DriveAuthorizationState(state);
        } catch (IllegalArgumentException unreadable) {
            throw new InvalidDriveAuthorizationException();
        }
    }
}
