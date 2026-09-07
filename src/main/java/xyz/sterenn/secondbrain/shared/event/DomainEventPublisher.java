package xyz.sterenn.secondbrain.shared.event;

/**
 * Within a transaction, the announcement only goes out on commit; outside one, immediately.
 */
public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
