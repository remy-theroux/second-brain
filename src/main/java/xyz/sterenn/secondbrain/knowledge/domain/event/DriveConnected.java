package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/** A Drive was just authorized, and it has no push subscription yet: the worker opens its first channel. */
public record DriveConnected(UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DriveConnected {
        Objects.requireNonNull(ownerId, "The owner of the connected Drive is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
    }
}
