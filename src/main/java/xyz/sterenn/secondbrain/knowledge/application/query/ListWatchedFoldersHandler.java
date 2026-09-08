package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class ListWatchedFoldersHandler implements QueryHandler<ListWatchedFolders, List<WatchedFolderView>> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final WatchedFolderRepository watchedFolderRepository;

    public ListWatchedFoldersHandler(
            DriveConnectionRepository driveConnectionRepository, WatchedFolderRepository watchedFolderRepository) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.watchedFolderRepository = watchedFolderRepository;
    }

    /** No connection yet is an empty list, not a refusal: the screen reads this before connecting anything. */
    @Override
    public List<WatchedFolderView> handle(ListWatchedFolders query) {
        return driveConnectionRepository
                .findByOwnerId(query.ownerId())
                .map(DriveConnection::getId)
                .map(connectionId -> watchedFolderRepository.findAllByConnectionId(connectionId).stream()
                        .map(WatchedFolderView::of)
                        .toList())
                .orElseGet(List::of);
    }
}
