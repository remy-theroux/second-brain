package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentTextExtracted(UUID documentId, UUID ownerId, int blockCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextExtracted {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
        if (blockCount <= 0) {
            throw new IllegalArgumentException("Une extraction sans bloc n'a rien à annoncer");
        }
    }
}
