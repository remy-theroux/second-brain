package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.sterenn.secondbrain.knowledge.application.agent.ConversationAgent;
import xyz.sterenn.secondbrain.knowledge.application.agent.ConversationOutcome;
import xyz.sterenn.secondbrain.knowledge.application.command.RecordAgentRun;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.LlmUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;
import xyz.sterenn.secondbrain.shared.web.ValidationErrorResponse;

@RestController
public class AskAgentController {

    /**
     * Budget de l'agent (120 s) + un tour au pire, le délai de lecture d'Ollama (180 s) + 30 s
     * de marge : la génération peut légitimement dépasser le budget pendant son dernier tour.
     */
    private static final long TIMEOUT_MILLIS = 330_000L;

    private static final Logger LOG = LoggerFactory.getLogger(AskAgentController.class);

    private final ConversationAgent conversationAgent;
    private final CommandBus commandBus;
    private final ExecutorService conversationExecutor;

    public AskAgentController(
            ConversationAgent conversationAgent,
            CommandBus commandBus,
            @Qualifier("conversationExecutor") ExecutorService conversationExecutor) {
        this.conversationAgent = conversationAgent;
        this.commandBus = commandBus;
        this.conversationExecutor = conversationExecutor;
    }

    @PostMapping("/api/chat")
    @SecurityRequirement(name = "bearer")
    public SseEmitter chat(@RequestBody AskAgentRequest request, @AuthenticationPrincipal Jwt jwt) {
        // Validé sur le thread servlet : une question refusée doit rendre 422, pas un flux.
        Question question = conversationAgent.validate(request.question());
        UUID ownerId = JwtSubject.accountId(jwt);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        AtomicBoolean expire = new AtomicBoolean(false);
        enregistreLeCycleDeVie(emitter, ownerId, expire);
        conversationExecutor.execute(() -> conduire(emitter, question, ownerId, expire));
        return emitter;
    }

    /** Demandés par le ticket : ils journalisent le cycle de vie du flux, l'ownerId en contexte. */
    private void enregistreLeCycleDeVie(SseEmitter emitter, UUID ownerId, AtomicBoolean expire) {
        emitter.onTimeout(() -> {
            expire.set(true);
            LOG.warn(
                    "Le flux SSE a expiré après {} ms sans que la conversation ne se termine (ownerId={}).",
                    TIMEOUT_MILLIS,
                    ownerId);
        });
        emitter.onError(erreur -> LOG.warn("Le flux SSE s'est terminé en erreur (ownerId={}).", ownerId, erreur));
        emitter.onCompletion(() -> LOG.debug("Le flux SSE est terminé (ownerId={}).", ownerId));
    }

    private void conduire(SseEmitter emitter, Question question, UUID ownerId, AtomicBoolean expire) {
        try {
            ConversationOutcome resultat =
                    conversationAgent.answer(question, ownerId, fragment -> emettre(emitter, "token", fragment));
            emettre(
                    emitter,
                    "sources",
                    resultat.answer().sources().stream().map(SourceView::of).toList());
            emettre(
                    emitter,
                    "done",
                    Map.of("verdict", resultat.answer().verdict().name()));
            emitter.complete();
            tracer(question, ownerId, resultat);
        } catch (ClientGoneException clientParti) {
            // Une expiration marque déjà l'emitter complet : le compléter à nouveau n'a pas lieu d'être.
            if (expire.get()) {
                LOG.info("La génération a été interrompue par l'expiration du flux (ownerId={}).", ownerId);
            } else {
                LOG.info("Le client a fermé sa connexion : la génération est interrompue (ownerId={}).", ownerId);
                emitter.complete();
            }
        } catch (LlmUnavailableException | EmbeddingUnavailableException serviceInjoignable) {
            LOG.error(
                    "La conversation a échoué : un service d'IA n'a pas répondu (ownerId={}).",
                    ownerId,
                    serviceInjoignable);
            echouer(
                    emitter,
                    ownerId,
                    "La conversation est momentanément indisponible : un service d'IA n'a pas "
                            + "répondu. Réessayez dans quelques instants.");
        } catch (RuntimeException echec) {
            LOG.error("La conversation a échoué (ownerId={}).", ownerId, echec);
            echouer(emitter, ownerId, "La conversation a échoué. Réessayez dans quelques instants.");
        }
    }

    /** La trace est écrite APRÈS la fermeture du flux : son échec ne doit rien coûter à une réponse déjà livrée. */
    private void tracer(Question question, UUID ownerId, ConversationOutcome resultat) {
        try {
            commandBus.dispatch(new RecordAgentRun(
                    ownerId,
                    conversationAgent.agentName(),
                    conversationAgent.agentVersion(),
                    question.value(),
                    resultat.answer().text(),
                    resultat.answer().verdict(),
                    resultat.tours(),
                    resultat.duree().toMillis(),
                    resultat.recherches(),
                    resultat.answer().sources()));
        } catch (RuntimeException tracePerdue) {
            LOG.error("La trace de cette conversation n'a pas pu être écrite (ownerId={}).", ownerId, tracePerdue);
        }
    }

    private void echouer(SseEmitter emitter, UUID ownerId, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(new ErrorResponse(message)));
        } catch (IOException | IllegalStateException clientDejaParti) {
            LOG.info("Le client était déjà parti quand l'erreur a été émise (ownerId={}).", ownerId);
        }
        emitter.complete();
    }

    private void emettre(SseEmitter emitter, String evenement, Object donnees) {
        try {
            emitter.send(SseEmitter.event().name(evenement).data(donnees));
        } catch (IOException | IllegalStateException clientParti) {
            throw new ClientGoneException(clientParti);
        }
    }

    @ExceptionHandler(InvalidQuestionException.class)
    public ResponseEntity<Object> questionIllisible(InvalidQuestionException refus) {
        return ResponseEntity.unprocessableEntity()
                .body(new ValidationErrorResponse(Map.of("question", refus.getMessage())));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> sujetIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** Traverse la boucle sans être rattrapée : c'est ainsi qu'une déconnexion arrête la génération. */
    private static final class ClientGoneException extends RuntimeException {

        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }
}
