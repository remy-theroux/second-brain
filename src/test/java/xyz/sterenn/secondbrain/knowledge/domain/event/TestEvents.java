package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Ils vivent dans un package du contexte {@code knowledge} parce que {@code DomainEventNames}
 * refuse tout ce qui est hors d'un contexte borné : un record déclaré dans le test lui-même
 * serait rejeté avant d'être nommé.
 */
public final class TestEvents {

    private TestEvents() {}

    /** Un seul mot : aucun objet, donc pas de forme à trois segments. */
    public record Uploaded(Instant occurredAt) implements DomainEvent {}
}
