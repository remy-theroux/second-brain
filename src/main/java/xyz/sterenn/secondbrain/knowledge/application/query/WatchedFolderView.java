package xyz.sterenn.secondbrain.knowledge.application.query;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;

public record WatchedFolderView(UUID id, String driveFolderId, String name, Instant watchedAt) {

    public static WatchedFolderView of(WatchedFolder watchedFolder) {
        return new WatchedFolderView(
                watchedFolder.getId(),
                watchedFolder.getDriveFolderId(),
                watchedFolder.getName(),
                watchedFolder.getWatchedAt());
    }
}
