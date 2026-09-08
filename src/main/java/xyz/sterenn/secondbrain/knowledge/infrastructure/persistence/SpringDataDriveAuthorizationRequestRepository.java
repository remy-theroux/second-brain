package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveAuthorizationRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;

interface SpringDataDriveAuthorizationRequestRepository extends JpaRepository<DriveAuthorizationRequest, UUID> {

    Optional<DriveAuthorizationRequest> findByState(DriveAuthorizationState state);
}
