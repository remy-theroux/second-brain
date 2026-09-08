package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveAccess;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class BrowseDriveFoldersHandler implements QueryHandler<BrowseDriveFolders, List<DriveFolderView>> {

    private final DriveConnectionRepository driveConnectionRepository;
    private final DriveAccess driveAccess;
    private final GoogleDriveFolders googleDriveFolders;

    public BrowseDriveFoldersHandler(
            DriveConnectionRepository driveConnectionRepository,
            DriveAccess driveAccess,
            GoogleDriveFolders googleDriveFolders) {
        this.driveConnectionRepository = driveConnectionRepository;
        this.driveAccess = driveAccess;
        this.googleDriveFolders = googleDriveFolders;
    }

    /** The connection is the one of the token holder: no connection identifier travels in the request. */
    @Override
    public List<DriveFolderView> handle(BrowseDriveFolders query) {
        DriveConnection connection =
                driveConnectionRepository.findByOwnerId(query.ownerId()).orElseThrow(DriveNotConnectedException::new);

        return driveAccess
                .call(connection, accessToken -> googleDriveFolders.children(accessToken, parentOf(query)))
                .stream()
                .map(DriveFolderView::of)
                .toList();
    }

    private static String parentOf(BrowseDriveFolders query) {
        return query.parentId() == null || query.parentId().isBlank()
                ? DriveFolder.ROOT
                : query.parentId().trim();
    }
}
