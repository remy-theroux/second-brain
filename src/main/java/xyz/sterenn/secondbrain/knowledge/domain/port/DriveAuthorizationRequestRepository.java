package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveAuthorizationRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;

/**
 * The callback carries no token: the request is found by its state alone, which is what
 * hands back the owner of the authorization under way.
 */
public interface DriveAuthorizationRequestRepository {

    DriveAuthorizationRequest save(DriveAuthorizationRequest driveAuthorizationRequest);

    Optional<DriveAuthorizationRequest> findByState(DriveAuthorizationState state);
}
