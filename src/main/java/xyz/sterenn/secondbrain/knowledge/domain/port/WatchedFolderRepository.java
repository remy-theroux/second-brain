package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;

/** Watched folders hang from a Drive connection: every read carries the connection they belong to. */
public interface WatchedFolderRepository {

    WatchedFolder save(WatchedFolder watchedFolder);

    List<WatchedFolder> findAllByConnectionId(UUID connectionId);

    Optional<WatchedFolder> findByIdAndConnectionId(UUID id, UUID connectionId);

    void delete(WatchedFolder watchedFolder);
}
