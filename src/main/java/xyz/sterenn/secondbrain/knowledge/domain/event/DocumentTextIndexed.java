package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentTextIndexed(UUID documentId, UUID ownerId, int chunkCount, Instant occurredAt)
        implements DomainEvent {

    public DocumentTextIndexed {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("Une indexation sans extrait n'a rien à annoncer");
        }
    }
}
