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
 * L'envoi est différé à {@code afterCommit} : un rollback n'annonce rien. L'inverse n'est pas
 * garanti — un broker injoignable après le commit perd l'événement, qui est journalisé et non
 * propagé, l'écriture étant déjà acquise (ADR-0023).
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
        // Dérivé ici et non dans afterCommit : un événement hors de tout contexte borné doit
        // faire échouer la commande avant le commit, pas remonter à l'appelant après.
        String name = DomainEventNames.of(event.getClass());
        // Les deux contrôles : une synchronisation peut être active sans qu'aucune transaction
        // ne le soit, et il n'y aurait alors jamais d'afterCommit — événement perdu en silence.
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
                            "Événement {} perdu : le broker n'a pas pu être joint après le commit ({})",
                            name,
                            e.getMessage(),
                            e);
                }
            }
        });
    }

    private void send(String name, DomainEvent event) {
        rabbitTemplate.convertAndSend(AmqpConfiguration.EVENTS_EXCHANGE, name, event);
        log.debug("Événement {} publié", name);
    }
}
