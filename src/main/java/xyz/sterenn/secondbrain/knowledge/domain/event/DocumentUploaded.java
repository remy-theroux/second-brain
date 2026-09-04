package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DocumentUploaded(UUID documentId, UUID ownerId, Instant occurredAt) implements DomainEvent {

    public DocumentUploaded {
        Objects.requireNonNull(documentId, "L'identifiant du document est obligatoire");
        Objects.requireNonNull(ownerId, "Le propriétaire du document est obligatoire");
        Objects.requireNonNull(occurredAt, "L'instant de l'événement est obligatoire");
    }
}
