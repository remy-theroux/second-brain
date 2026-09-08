package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class ListWatchedFoldersHandler implements QueryHandler<ListWatchedFolders, List<WatchedFolderView>> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;
    private final DocumentRepository documentRepository;

    public ListWatchedFoldersHandler(
            DriveConnectionRepository driveConnectionRepository,
            WatchedFolderRepository watchedFolderRepository,
            DocumentRepository documentRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
        this.documentRepository = documentRepository;
    }

    /** No connection yet is an empty list, not a refusal: the screen reads this before connecting anything. */
    @Override
    public List<WatchedFolderView> handle(ListWatchedFolders query) {
        return driveConnectionRepository
                .findByOwnerId(query.ownerId())
                .map(DriveConnection::getId)
                .map(connectionId -> folders(query.ownerId(), connectionId))
                .orElseGet(List::of);
    }

    private List<WatchedFolderView> folders(UUID ownerId, UUID connectionId) {
        Map<UUID, Long> documentCounts = documentRepository.countDocumentsByWatchedFolder(ownerId);
        return watchedFolderRepository.findAllByConnectionId(connectionId).stream()
                .map(watchedFolder ->
                        WatchedFolderView.of(watchedFolder, documentCounts.getOrDefault(watchedFolder.getId(), 0L)))
                .toList();
    }
}
