package xyz.sterenn.secondbrain.knowledge.domain.port;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelSubscription;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;

/** The push subscription of a Drive: Google calls us back rather than being asked every N minutes. */
public interface GoogleDriveChannels {

    /**
     * Opens a channel on the change feed. The identifier and the token are ours; the resource and
     * the deadline come back from Google, and the deadline is read rather than assumed.
     *
     * @param pageToken where the feed starts, which Google demands and we never use: a
     *     notification says that something moved, never what.
     */
    DriveChannelSubscription watch(
            DriveAccessToken accessToken, String channelId, DriveChannelToken token, String address, String pageToken);

    /** Idempotent: a channel Google no longer knows is a channel that is closed. */
    void stop(DriveAccessToken accessToken, String channelId, String resourceId);
}
