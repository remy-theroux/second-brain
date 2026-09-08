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
import xyz.sterenn.secondbrain.knowledge.application.command.RenewDriveChannel;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveFailures;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveFolderImporter;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveSynchroniser;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentContentReplaced;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveConnected;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveFolderImportRequested;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentProcessingException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

@Component
@Profile("worker")
@RabbitListener(queues = KnowledgeMessagingConfiguration.KNOWLEDGE_EVENTS_QUEUE)
public class KnowledgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventListener.class);

    private static final String UNEXPECTED_FAILURE = "Le traitement de ce document a échoué de façon inattendue.";

    private final CommandBus commandBus;
    private final DriveFolderImporter driveFolderImporter;
    private final DriveSynchroniser driveSynchroniser;

    public KnowledgeEventListener(
            CommandBus commandBus, DriveFolderImporter driveFolderImporter, DriveSynchroniser driveSynchroniser) {
        this.commandBus = commandBus;
        this.driveFolderImporter = driveFolderImporter;
        this.driveSynchroniser = driveSynchroniser;
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
     * The only handler that does not dispatch a command: the importer holds the delivery for the
     * whole walk, and dispatches one command per file so as not to hold one transaction for it.
     */
    @RabbitHandler
    public void on(DriveFolderImportRequested event) {
        driveFolderImporter.importFolder(event.ownerId(), event.watchedFolderId());
    }

    /**
     * The first channel of a Drive, opened as soon as it is connected rather than at the next
     * renewal round: an hour without any subscription is exactly the hour its owner spends
     * dropping files into it. A channel that does not open costs a delay and nothing else — the
     * round retries within the hour, and the periodic scan keeps the base honest meanwhile.
     */
    @RabbitHandler
    public void on(DriveConnected event) {
        try {
            commandBus.dispatch(new RenewDriveChannel(event.ownerId()));
        } catch (RuntimeException failure) {
            log.warn(
                    "The first channel of the Drive of {} could not be opened: {}",
                    event.ownerId(),
                    DriveFailures.describe(failure));
        }
    }

    /**
     * Same shape as the import above, and the same reason: the round holds the delivery, and it
     * is the one place where two rounds are kept from overlapping — the container consumes one
     * message at a time.
     */
    @RabbitHandler
    public void on(DriveSynchronisationRequested event) {
        driveSynchroniser.synchronise(event.ownerId());
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
