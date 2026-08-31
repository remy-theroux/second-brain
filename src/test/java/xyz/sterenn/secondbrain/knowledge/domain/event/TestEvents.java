package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * Événements d'essai pour la convention de nommage du transport. Ils vivent dans un package
 * du contexte {@code knowledge} parce que {@code DomainEventNames} refuse tout ce qui est hors
 * d'un contexte borné — un record déclaré dans le test lui-même serait rejeté avant d'être
 * nommé. Aucun n'est déclaré au convertisseur : ils ne voyagent jamais.
 */
public final class TestEvents {

    private TestEvents() {
        // conteneur de records d'essai
    }

    /** Un seul mot : aucun objet, la forme à trois segments n'est pas atteignable. */
    public record Uploaded(Instant occurredAt) implements DomainEvent {}
}
