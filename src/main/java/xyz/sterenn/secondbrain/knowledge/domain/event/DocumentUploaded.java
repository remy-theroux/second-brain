package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentUploaded(UUID documentId, UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DocumentUploaded {
        Objects.requireNonNull(documentId, "The document id is required");
        Objects.requireNonNull(ownerId, "The document owner is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
    }
}
