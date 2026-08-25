package xyz.sterenn.secondbrain.shared.event.amqp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;
import xyz.sterenn.secondbrain.shared.event.DomainEventPublisher;

/**
 * Adapter du port {@link DomainEventPublisher} sur RabbitMQ.
 *
 * <p>Depuis une transaction, l'envoi est différé à {@code afterCommit} : la base a commité,
 * l'événement peut être annoncé. Un rollback ne l'annonce jamais — pas d'événement fantôme
 * désignant une ligne qui n'existe pas. C'est la garantie qui compte pour un consommateur
 * qui va relire le document.
 *
 * <p>L'inverse n'est pas garanti : si le broker est injoignable dans {@code afterCommit},
 * l'écriture est acquise et l'événement est perdu. L'exception est journalisée, pas
 * propagée — elle ne peut plus annuler le commit, et elle ne doit pas faire échouer une
 * requête dont l'écriture a réussi. Pas d'outbox, pas de rattrapage : décision 3 de la
 * spec, écart assumé dans CLAUDE.md.
 *
 * <p>Hors transaction, l'envoi est immédiat et une panne du broker remonte à l'appelant :
 * il n'y a rien d'acquis à protéger.
 *
 * <p>Le nom de l'événement est dérivé dans {@code publish}, avant tout enregistrement :
 * un événement hors de tout contexte borné est une erreur de programmation, et elle doit
 * faire échouer la commande avant le commit — pas remonter à l'appelant après.
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
        // Dérivé ici et non dans afterCommit : un événement hors de tout contexte borné est
        // une erreur de programmation, elle doit faire échouer la commande AVANT le commit.
        String name = DomainEventNames.of(event.getClass());
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(name, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    send(name, event);
                } catch (AmqpException e) {
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
