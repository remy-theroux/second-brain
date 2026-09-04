package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.application.command.IndexDocumentText;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDocumentProcessingFailed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentProcessingException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

@Component
@Profile("worker")
@RabbitListener(queues = KnowledgeMessagingConfiguration.KNOWLEDGE_EVENTS_QUEUE)
public class KnowledgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventListener.class);

    private static final String ECHEC_INATTENDU = "Le traitement de ce document a échoué de façon inattendue.";

    private final CommandBus commandBus;

    public KnowledgeEventListener(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * L'échec se marque par une <em>seconde</em> commande, donc une seconde transaction : le
     * bus vient d'annuler la première, qui emporterait le statut avec elle. Voir ADR-0028.
     */
    @RabbitHandler
    public void on(DocumentUploaded event) {
        try {
            commandBus.dispatch(new ExtractDocumentText(event.documentId(), event.ownerId()));
        } catch (RuntimeException echec) {
            log.error("Extraction du document {} en échec", event.documentId(), echec);
            commandBus.dispatch(new MarkDocumentProcessingFailed(event.documentId(), event.ownerId(), motif(echec)));
        }
    }

    /** Même dispositif qu'au dépôt : le statut d'échec s'écrit hors de la transaction annulée (ADR-0028). */
    @RabbitHandler
    public void on(DocumentTextExtracted event) {
        try {
            commandBus.dispatch(new IndexDocumentText(event.documentId(), event.ownerId()));
        } catch (RuntimeException echec) {
            log.error("Indexation du document {} en échec", event.documentId(), echec);
            commandBus.dispatch(new MarkDocumentProcessingFailed(event.documentId(), event.ownerId(), motif(echec)));
        }
    }

    /**
     * Ce handler ne journalise que pour exister : un type déclaré dans
     * {@code DomainEventRegistration} sans {@code @RabbitHandler} est rejeté par Spring AMQP.
     */
    @RabbitHandler
    public void on(DocumentTextIndexed event) {
        log.info(
                "Événement knowledge.document-text.indexed reçu pour le document {} : {} extraits",
                event.documentId(),
                event.chunkCount());
    }

    private static String motif(RuntimeException echec) {
        return echec instanceof DocumentProcessingException refusMetier ? refusMetier.getMessage() : ECHEC_INATTENDU;
    }
}
