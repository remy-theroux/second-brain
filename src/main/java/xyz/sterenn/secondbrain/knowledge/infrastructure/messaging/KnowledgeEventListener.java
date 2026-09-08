package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.application.command.IndexDocumentText;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDocumentProcessingFailed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentContentReplaced;
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

    private static final String UNEXPECTED_FAILURE = "Le traitement de ce document a échoué de façon inattendue.";

    private final CommandBus commandBus;

    public KnowledgeEventListener(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @RabbitHandler
    public void on(DocumentUploaded event) {
        extract(event.documentId(), event.ownerId());
    }

    /** A replaced content re-enters the pipeline exactly where an upload does — same command, same repairs. */
    @RabbitHandler
    public void on(DocumentContentReplaced event) {
        extract(event.documentId(), event.ownerId());
    }

    /**
     * The failure is recorded by a <em>second</em> command, hence a second transaction: the bus
     * has just rolled the first one back, and it would take the status with it. See ADR-0028.
     */
    private void extract(UUID documentId, UUID ownerId) {
        try {
            commandBus.dispatch(new ExtractDocumentText(documentId, ownerId));
        } catch (RuntimeException failure) {
            log.error("Extraction of document {} failed", documentId, failure);
            commandBus.dispatch(new MarkDocumentProcessingFailed(documentId, ownerId, reason(failure)));
        }
    }

    /** Same device as on upload: the failure status is written outside the rolled-back transaction (ADR-0028). */
    @RabbitHandler
    public void on(DocumentTextExtracted event) {
        try {
            commandBus.dispatch(new IndexDocumentText(event.documentId(), event.ownerId()));
        } catch (RuntimeException failure) {
            log.error("Indexing of document {} failed", event.documentId(), failure);
            commandBus.dispatch(new MarkDocumentProcessingFailed(event.documentId(), event.ownerId(), reason(failure)));
        }
    }

    /**
     * This handler only logs so as to exist: a type declared in {@code DomainEventRegistration}
     * without a {@code @RabbitHandler} is rejected by Spring AMQP.
     */
    @RabbitHandler
    public void on(DocumentTextIndexed event) {
        log.info(
                "Event knowledge.document-text.indexed received for document {}: {} chunks",
                event.documentId(),
                event.chunkCount());
    }

    private static String reason(RuntimeException failure) {
        return failure instanceof DocumentProcessingException businessRefusal
                ? businessRefusal.getMessage()
                : UNEXPECTED_FAILURE;
    }
}
