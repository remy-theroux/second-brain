package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;

/** The Drive seen as a tree of folders: no file ever crosses this port, and no content is read. */
public interface GoogleDriveFolders {

    /** Every folder of {@code parentId}, page tokens followed, {@link DriveFolder#ROOT} for the top. */
    List<DriveFolder> children(DriveAccessToken accessToken, String parentId);
}
