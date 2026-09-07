package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

/**
 * They live in a package of the {@code knowledge} context because {@code DomainEventNames}
 * rejects anything outside a bounded context: a record declared in the test itself would be
 * rejected before being named.
 */
public final class TestEvents {

    private TestEvents() {}

    /** A single word: no object, therefore no three-segment form. */
    public record Uploaded(Instant occurredAt) implements DomainEvent {}
}
