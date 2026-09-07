package xyz.sterenn.secondbrain.shared.event.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Sending is deferred to {@code afterCommit}: a rollback announces nothing. The converse is not
 * guaranteed — a broker unreachable after the commit loses the event, which is logged and not
 * propagated, the write being already acquired (ADR-0023).
 */
@Component
public class AmqpDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AmqpDomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public AmqpDomainEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(DomainEvent event) {
        // Derived here and not in afterCommit: an event outside any bounded context must fail
        // the command before the commit, not surface to the caller afterwards.
        String name = DomainEventNames.of(event.getClass());
        // Both checks: a synchronisation can be active without any transaction being active,
        // and there would then never be an afterCommit — event lost in silence.
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            send(name, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    send(name, event);
                } catch (RuntimeException e) {
                    log.error(
                            "Event {} lost: the broker could not be reached after the commit ({})",
                            name,
                            e.getMessage(),
                            e);
                }
            }
        });
    }

    private void send(String name, DomainEvent event) {
        rabbitTemplate.convertAndSend(AmqpConfiguration.EVENTS_EXCHANGE, name, event);
        log.debug("Event {} published", name);
    }
}
