package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;

interface SpringDataWatchedFolderRepository extends JpaRepository<WatchedFolder, UUID> {

    List<WatchedFolder> findAllByConnectionIdOrderByWatchedAtAsc(UUID connectionId);

    Optional<WatchedFolder> findByIdAndConnectionId(UUID id, UUID connectionId);
}
