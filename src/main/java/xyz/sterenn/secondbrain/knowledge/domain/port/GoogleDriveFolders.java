package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.Optional;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolderChain;

/** The Drive seen as a tree of folders: no file ever crosses this port, and no content is read. */
public interface GoogleDriveFolders {

    /** Every folder of {@code parentId}, page tokens followed, {@link DriveFolder#ROOT} for the top. */
    List<DriveFolder> children(DriveAccessToken accessToken, String parentId);

    /** Empty when the Drive of this token knows no such folder. */
    Optional<DriveFolder> folder(DriveAccessToken accessToken, String folderId);

    /**
     * The folders above {@code folderId}, nearest first, up to the top of the Drive — the folder
     * itself is not one of them. The chain says whether it got there: a climb that broke on a
     * parent nothing could read hands back what it saw and says so, never a chain a caller could
     * read as "under no watched folder".
     */
    DriveFolderChain ancestors(DriveAccessToken accessToken, String folderId);
}
