package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentContentReplaced(UUID documentId, UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DocumentContentReplaced {
        Objects.requireNonNull(documentId, "The document id is required");
        Objects.requireNonNull(ownerId, "The document owner is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
    }
}
