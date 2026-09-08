package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Instant;
import java.util.Objects;

/**
 * What Google hands back when a channel is opened. {@code resourceId} is half of what closing it
 * takes — {@code channels.stop} demands the channel identifier <em>and</em> this one — and
 * {@code expiresAt} is read, never assumed: seven days is a maximum, not a promise.
 */
public record DriveChannelSubscription(String resourceId, Instant expiresAt) {

    public DriveChannelSubscription {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("A channel without its resource identifier can no longer be stopped");
        }
        Objects.requireNonNull(expiresAt, "The deadline of the channel is required");
    }
}
