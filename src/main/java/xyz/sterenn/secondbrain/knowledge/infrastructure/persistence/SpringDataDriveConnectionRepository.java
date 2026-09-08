package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;

interface SpringDataDriveConnectionRepository extends JpaRepository<DriveConnection, UUID> {

    Optional<DriveConnection> findByOwnerId(UUID ownerId);
}
