package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Instant;
import java.util.Objects;

/**
 * What Google hands back when a channel is opened. {@code resourceId} is half of what closing it
 * takes — {@code channels.stop} demands the channel identifier <em>and</em> this one — and
 * {@code expiresAt} is read, never assumed: the seven days are asked for in the watch itself, and
 * Google may hand back less without a word — one hour is what it grants a watch that asks for
 * nothing at all.
 */
public record DriveChannelSubscription(String resourceId, Instant expiresAt) {

    public DriveChannelSubscription {
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("A channel without its resource identifier can no longer be stopped");
        }
        Objects.requireNonNull(expiresAt, "The deadline of the channel is required");
    }
}
