package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;

interface SpringDataDriveConnectionRepository extends JpaRepository<DriveConnection, UUID> {

    Optional<DriveConnection> findByOwnerId(UUID ownerId);

    List<DriveConnection> findAllByStatus(DriveConnectionStatus status);
}
