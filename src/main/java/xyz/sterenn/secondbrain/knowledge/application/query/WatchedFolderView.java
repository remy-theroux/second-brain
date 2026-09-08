package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportRejection;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;

/**
 * A folder never imported has neither instant nor status: their absence is what says so. The
 * rejections travel with the folder rather than behind a route of their own — the folder loads
 * them anyway, and the screen shows them next to the outcome they belong to.
 */
public record WatchedFolderView(
        UUID id,
        String driveFolderId,
        String name,
        Instant watchedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant lastImportAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) DriveImportStatus lastImportStatus,
        @JsonInclude(JsonInclude.Include.NON_NULL) String lastImportError,
        long documentCount,
        List<RejectionView> rejections) {

    public record RejectionView(String filename, String reason) {}

    public static WatchedFolderView of(WatchedFolder watchedFolder, long documentCount) {
        return new WatchedFolderView(
                watchedFolder.getId(),
                watchedFolder.getDriveFolderId(),
                watchedFolder.getName(),
                watchedFolder.getWatchedAt(),
                watchedFolder.getLastImportAt(),
                watchedFolder.getLastImportStatus(),
                watchedFolder.getLastImportError(),
                documentCount,
                watchedFolder.getRejections().stream()
                        .map(WatchedFolderView::rejectionOf)
                        .toList());
    }

    private static RejectionView rejectionOf(DriveImportRejection rejection) {
        return new RejectionView(rejection.getFilename(), rejection.getReason());
    }
}
