package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentTextIndexed(UUID documentId, UUID ownerId, int chunkCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextIndexed {
        Objects.requireNonNull(documentId, "The document id is required");
        Objects.requireNonNull(ownerId, "The document owner is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("An indexing without a chunk has nothing to announce");
        }
    }
}
