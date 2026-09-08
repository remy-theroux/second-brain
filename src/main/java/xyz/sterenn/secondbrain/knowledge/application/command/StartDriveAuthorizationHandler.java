package xyz.sterenn.secondbrain.knowledge.application.command;

import java.time.Clock;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveAuthorizationRequest;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveAuthorizationRequestRepository;
import xyz.sterenn.secondbrain.shared.bus.CommandHandler;

@Component
public class StartDriveAuthorizationHandler implements CommandHandler<StartDriveAuthorization> {

    private final DriveAuthorizationRequestRepository driveAuthorizationRequestRepository;
    private final Clock clock;

    public StartDriveAuthorizationHandler(
            DriveAuthorizationRequestRepository driveAuthorizationRequestRepository, Clock clock) {
        this.driveAuthorizationRequestRepository = driveAuthorizationRequestRepository;
        this.clock = clock;
    }

    @Override
    public void handle(StartDriveAuthorization command) {
        driveAuthorizationRequestRepository.save(
                DriveAuthorizationRequest.open(command.ownerId(), command.state(), clock.instant()));
    }
}
