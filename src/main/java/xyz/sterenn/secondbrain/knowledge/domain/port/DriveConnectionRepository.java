package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;

/** An account holds at most one Drive connection: every read carries its owner. */
public interface DriveConnectionRepository {

    DriveConnection save(DriveConnection driveConnection);

    Optional<DriveConnection> findByOwnerId(UUID ownerId);

    /** By its own identifier, which is how a channel — and only a channel — names its connection. */
    Optional<DriveConnection> findById(UUID id);

    /** Every connection a synchronisation can still read: a withdrawn authorization opens nothing. */
    List<DriveConnection> findAllActive();

    void delete(DriveConnection driveConnection);
}
