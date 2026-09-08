package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.ConstraintViolationException.ConstraintKind;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.FolderAlreadyCoveredException;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;

@Component
public class JpaWatchedFolderRepositoryAdapter implements WatchedFolderRepository {

    private final SpringDataWatchedFolderRepository springDataWatchedFolderRepository;

    JpaWatchedFolderRepositoryAdapter(SpringDataWatchedFolderRepository springDataWatchedFolderRepository) {
        this.springDataWatchedFolderRepository = springDataWatchedFolderRepository;
    }

    @Override
    public WatchedFolder save(WatchedFolder watchedFolder) {
        try {
            // Without an explicit flush, the uniqueness of (connection_id, drive_folder_id) would
            // only surface at commit, out of reach of this catch.
            return springDataWatchedFolderRepository.saveAndFlush(watchedFolder);
        } catch (DataIntegrityViolationException violation) {
            // Only uniqueness means "already covered", and the covering folder is then itself, two
            // simultaneous watches having raced. The foreign key of a connection deleted meanwhile
            // would otherwise name a covering folder that never existed.
            if (!isUniquenessConflict(violation)) {
                throw violation;
            }
            throw new FolderAlreadyCoveredException(watchedFolder.getName());
        }
    }

    private static boolean isUniquenessConflict(DataIntegrityViolationException violation) {
        return violation.getCause() instanceof ConstraintViolationException constraint
                && constraint.getKind() == ConstraintKind.UNIQUE;
    }

    @Override
    public List<WatchedFolder> findAllByConnectionId(UUID connectionId) {
        return springDataWatchedFolderRepository.findAllByConnectionIdOrderByWatchedAtAsc(connectionId);
    }

    @Override
    public Optional<WatchedFolder> findByIdAndConnectionId(UUID id, UUID connectionId) {
        return springDataWatchedFolderRepository.findByIdAndConnectionId(id, connectionId);
    }

    @Override
    public void delete(WatchedFolder watchedFolder) {
        springDataWatchedFolderRepository.delete(watchedFolder);
        springDataWatchedFolderRepository.flush();
    }
}
