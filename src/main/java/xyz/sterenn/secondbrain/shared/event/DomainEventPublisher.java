package xyz.sterenn.secondbrain.shared.event;

/**
 * Depuis une transaction, l'annonce ne part qu'au commit ; hors transaction, immédiatement.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
