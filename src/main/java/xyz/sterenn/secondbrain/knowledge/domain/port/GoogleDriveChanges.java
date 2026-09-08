package xyz.sterenn.secondbrain.knowledge.domain.port;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChangePage;

/** The Drive seen as a feed of changes: where to start reading, then what moved since. */
public interface GoogleDriveChanges {

    /** The token a Drive with no history behind it starts from: everything after this instant. */
    String startPageToken(DriveAccessToken accessToken);

    /**
     * Everything that moved since {@code pageToken}, page tokens followed, and the token of the
     * next run.
     *
     * @throws xyz.sterenn.secondbrain.knowledge.domain.exception.DriveChangeTokenExpiredException
     *     when Google no longer knows the token: what is owed then is a full scan, never a fresh
     *     starting point
     */
    DriveChangePage changesSince(DriveAccessToken accessToken, String pageToken);
}
