package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentTextExtracted(UUID documentId, UUID ownerId, int blockCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextExtracted {
        Objects.requireNonNull(documentId, "The document id is required");
        Objects.requireNonNull(ownerId, "The document owner is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
        if (blockCount <= 0) {
            throw new IllegalArgumentException("An extraction without a block has nothing to announce");
        }
    }
}
