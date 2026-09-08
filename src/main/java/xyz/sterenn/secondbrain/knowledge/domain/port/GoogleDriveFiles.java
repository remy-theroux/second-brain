package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;

/** The Drive seen as the content of a watched folder: what it holds, then the bytes of one file. */
public interface GoogleDriveFiles {

    /**
     * Every readable file of {@code folderId} and of its subfolders, page tokens followed. A file
     * in a format this base cannot read is not one of them, and is not an error either.
     */
    List<DriveFile> filesUnder(DriveAccessToken accessToken, String folderId);

    /** The bytes of one file, exactly as Drive holds them. */
    byte[] download(DriveAccessToken accessToken, String fileId);

    /**
     * The bytes Google makes of a native Doc, which holds none of its own: an archive rebuilt on
     * every call, so two exports of an untouched document never carry the same checksum.
     */
    byte[] export(DriveAccessToken accessToken, String fileId);
}
