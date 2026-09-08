package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;

/** An account holds at most one Drive connection: every read carries its owner. */
public interface DriveConnectionRepository {

    DriveConnection save(DriveConnection driveConnection);

    Optional<DriveConnection> findByOwnerId(UUID ownerId);

    void delete(DriveConnection driveConnection);
}
