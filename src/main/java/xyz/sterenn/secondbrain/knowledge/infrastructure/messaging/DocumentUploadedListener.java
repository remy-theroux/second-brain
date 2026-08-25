package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;

/**
 * Adapter entrant : reçoit {@link DocumentUploaded} depuis la queue d'extraction.
 *
 * <p>Un listener, une queue, une commande — la règle « une classe de contrôleur, un
 * mapping » vaut ici aussi. Il désérialise et dispatche ; aucune règle métier.
 *
 * <p>{@code @Profile("worker")} : l'API publie, elle ne consomme jamais. Une exception
 * levée ici rejette le message sans remise en file
 * ({@code default-requeue-rejected=false} dans {@code application.yml}) : un échec doit
 * finir en {@code FAILED} sur le document, pas être rejoué.
 */
@Component
@Profile("worker")
public class DocumentUploadedListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadedListener.class);

    @RabbitListener(queues = KnowledgeMessagingConfiguration.EXTRACTION_QUEUE)
    public void on(DocumentUploaded event) {
        // Tant qu'aucune commande d'extraction n'existe, recevoir se constate au journal.
        // Le plan d'extraction remplace cette ligne par
        // commandBus.dispatch(new ExtractDocumentText(event.documentId())).
        log.info("Événement knowledge.DocumentUploaded reçu pour le document {}", event.documentId());
    }
}
