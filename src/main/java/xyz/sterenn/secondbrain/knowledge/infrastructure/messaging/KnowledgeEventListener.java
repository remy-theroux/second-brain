package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;

/**
 * Adapter entrant : consomme la queue du contexte {@code knowledge}.
 *
 * <p>Un listener par contexte, un handler par événement. La queue reçoit tout ce que le
 * contexte annonce ({@code knowledge.#}), et deux classes {@code @RabbitListener} sur la
 * même queue se disputeraient les messages — celle qui ne connaît pas le type rejetterait
 * sans requeue, et l'événement serait perdu. D'où le {@code @RabbitListener} sur la classe
 * et un {@code @RabbitHandler} par type : c'est l'en-tête de type qui choisit la méthode.
 * Un type déclaré mais sans handler est refusé par Spring AMQP, et rejeté comme un type non
 * déclaré. Chaque handler désérialise et dispatche ; aucune règle métier.
 *
 * <p>{@code @Profile("worker")} : l'API publie, elle ne consomme jamais. Une exception
 * levée ici rejette le message sans remise en file
 * ({@code default-requeue-rejected=false} dans {@code application.yml}) : un échec doit
 * finir en {@code FAILED} sur le document, pas être rejoué.
 */
@Component
@Profile("worker")
@RabbitListener(queues = KnowledgeMessagingConfiguration.KNOWLEDGE_EVENTS_QUEUE)
public class KnowledgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventListener.class);

    @RabbitHandler
    public void on(DocumentUploaded event) {
        // Tant qu'aucune commande d'extraction n'existe, recevoir se constate au journal.
        // Le plan d'extraction remplace cette ligne par
        // commandBus.dispatch(new ExtractDocumentText(event.documentId())).
        log.info("Événement knowledge.document.uploaded reçu pour le document {}", event.documentId());
    }
}
