package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDocumentProcessingFailed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentProcessingException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

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
 * <p>{@code @Profile("worker")} : l'API publie, elle ne consomme jamais.
 */
@Component
@Profile("worker")
@RabbitListener(queues = KnowledgeMessagingConfiguration.KNOWLEDGE_EVENTS_QUEUE)
public class KnowledgeEventListener {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEventListener.class);

    /** Ce qu'on montre quand l'échec n'est pas un refus métier : rien de la panne elle-même. */
    private static final String ECHEC_INATTENDU = "Le traitement de ce document a échoué de façon inattendue.";

    private final CommandBus commandBus;

    public KnowledgeEventListener(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /**
     * Un document vient d'être déposé : on en extrait le texte.
     *
     * <p><strong>Le {@code catch} est la raison d'être de cette méthode.</strong> Le bus
     * ouvre la transaction et l'annule sur la moindre exception ; marquer l'échec depuis le
     * handler d'extraction le ferait disparaître avec le rollback, et le document resterait
     * éternellement en attente. La seconde commande ouvre donc sa <em>propre</em>
     * transaction. Voir ADR-0028.
     *
     * <p>Et l'exception n'est <strong>pas relevée</strong> : rejeter le message n'apporterait
     * rien, l'issue du traitement étant déjà en base. La relever ne produirait qu'une pile de
     * plus dans le journal.
     *
     * <p>Si la seconde commande échoue à son tour, elle, remonte : le message est rejeté sans
     * remise en file ({@code default-requeue-rejected=false}) et le document reste
     * {@code PENDING}. C'est le seul trou, il est journalisé, et il relève du même arbitrage
     * qu'ADR-0023 — on ne construit pas de filet au filet.
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

    /**
     * Le texte d'un document vient d'être extrait.
     *
     * <p>Ce handler ne fait rien d'autre que journaliser, et il doit pourtant exister : un
     * type déclaré dans {@code DomainEventRegistration} mais sans {@code @RabbitHandler} est
     * refusé par Spring AMQP et rejeté comme un type inconnu. RAG-5 remplacera cette ligne
     * par {@code commandBus.dispatch(new ChunkDocumentText(...))}.
     */
    @RabbitHandler
    public void on(DocumentTextExtracted event) {
        log.info(
                "Événement knowledge.document-text.extracted reçu pour le document {} : {} blocs",
                event.documentId(),
                event.blockCount());
    }

    /**
     * Les extraits d'un document viennent d'être rangés — la fin de la chaîne, pour l'instant.
     *
     * <p>Ce handler ne fait que journaliser, et il doit pourtant exister : un type déclaré dans
     * {@code DomainEventRegistration} mais sans {@code @RabbitHandler} est refusé par Spring
     * AMQP et rejeté comme un type inconnu.
     */
    @RabbitHandler
    public void on(DocumentTextIndexed event) {
        log.info(
                "Événement knowledge.document-text.indexed reçu pour le document {} : {} extraits",
                event.documentId(),
                event.chunkCount());
    }

    /**
     * Un refus métier porte un message affichable tel quel ; le reste n'en porte aucun qu'on
     * puisse montrer. Le message d'une {@code NullPointerException} n'a rien à faire sous les
     * yeux de l'utilisateur — il est dans le journal, où il sert.
     *
     * <p>C'est {@link DocumentProcessingException} qui est testée, et non la seule
     * {@code DocumentExtractionException} : un service de vectorisation injoignable doit
     * s'annoncer comme tel, sans quoi une URL mal saisie serait indiscernable d'un PDF
     * illisible.
     */
    private static String motif(RuntimeException echec) {
        return echec instanceof DocumentProcessingException refusMetier ? refusMetier.getMessage() : ECHEC_INATTENDU;
    }
}
