package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DriveSynchronisationRequested(UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DriveSynchronisationRequested {
        Objects.requireNonNull(ownerId, "The owner of the Drive to synchronise is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
    }
}
