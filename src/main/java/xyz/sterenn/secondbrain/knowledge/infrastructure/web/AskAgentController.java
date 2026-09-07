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
     * Agent budget (120 s) + one worst-case turn, the Ollama read timeout (180 s) + 30 s of
     * margin: generation may legitimately overrun the budget during its last turn.
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
        // Validated on the servlet thread: a refused question must return 422, not a stream.
        Question question = conversationAgent.validate(request.question());
        UUID ownerId = JwtSubject.accountId(jwt);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        AtomicBoolean timedOut = new AtomicBoolean(false);
        logLifecycle(emitter, ownerId, timedOut);
        conversationExecutor.execute(() -> converse(emitter, question, ownerId, timedOut));
        return emitter;
    }

    /** Asked for by the ticket: they log the stream's lifecycle, with the ownerId in context. */
    private void logLifecycle(SseEmitter emitter, UUID ownerId, AtomicBoolean timedOut) {
        emitter.onTimeout(() -> {
            timedOut.set(true);
            LOG.warn(
                    "Le flux SSE a expiré après {} ms sans que la conversation ne se termine (ownerId={}).",
                    TIMEOUT_MILLIS,
                    ownerId);
        });
        emitter.onError(error -> LOG.warn("Le flux SSE s'est terminé en erreur (ownerId={}).", ownerId, error));
        emitter.onCompletion(() -> LOG.debug("Le flux SSE est terminé (ownerId={}).", ownerId));
    }

    private void converse(SseEmitter emitter, Question question, UUID ownerId, AtomicBoolean timedOut) {
        try {
            ConversationOutcome outcome =
                    conversationAgent.answer(question, ownerId, fragment -> emit(emitter, "token", fragment));
            emit(
                    emitter,
                    "sources",
                    outcome.answer().sources().stream().map(SourceView::of).toList());
            emit(emitter, "done", Map.of("verdict", outcome.answer().verdict().name()));
            emitter.complete();
            trace(question, ownerId, outcome);
        } catch (ClientGoneException clientGone) {
            // A timeout already marks the emitter complete: completing it again has no purpose.
            if (timedOut.get()) {
                LOG.info("La génération a été interrompue par l'expiration du flux (ownerId={}).", ownerId);
            } else {
                LOG.info("Le client a fermé sa connexion : la génération est interrompue (ownerId={}).", ownerId);
                emitter.complete();
            }
        } catch (LlmUnavailableException | EmbeddingUnavailableException unreachableService) {
            LOG.error(
                    "La conversation a échoué : un service d'IA n'a pas répondu (ownerId={}).",
                    ownerId,
                    unreachableService);
            fail(
                    emitter,
                    ownerId,
                    "La conversation est momentanément indisponible : un service d'IA n'a pas "
                            + "répondu. Réessayez dans quelques instants.");
        } catch (RuntimeException failure) {
            LOG.error("La conversation a échoué (ownerId={}).", ownerId, failure);
            fail(emitter, ownerId, "La conversation a échoué. Réessayez dans quelques instants.");
        }
    }

    /** The trace is written AFTER the stream is closed: its failure must cost nothing to a delivered answer. */
    private void trace(Question question, UUID ownerId, ConversationOutcome outcome) {
        try {
            commandBus.dispatch(new RecordAgentRun(
                    ownerId,
                    conversationAgent.agentName(),
                    conversationAgent.agentVersion(),
                    question.value(),
                    outcome.answer().text(),
                    outcome.answer().verdict(),
                    outcome.turns(),
                    outcome.duration().toMillis(),
                    outcome.searches(),
                    outcome.answer().sources()));
        } catch (RuntimeException lostTrace) {
            LOG.error("La trace de cette conversation n'a pas pu être écrite (ownerId={}).", ownerId, lostTrace);
        }
    }

    private void fail(SseEmitter emitter, UUID ownerId, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(new ErrorResponse(message)));
        } catch (IOException | IllegalStateException clientAlreadyGone) {
            LOG.info("Le client était déjà parti quand l'erreur a été émise (ownerId={}).", ownerId);
        }
        emitter.complete();
    }

    private void emit(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException clientGone) {
            throw new ClientGoneException(clientGone);
        }
    }

    @ExceptionHandler(InvalidQuestionException.class)
    public ResponseEntity<Object> unreadableQuestion(InvalidQuestionException refusal) {
        return ResponseEntity.unprocessableEntity()
                .body(new ValidationErrorResponse(Map.of("question", refusal.getMessage())));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /** Crosses the loop uncaught: that is how a disconnection stops the generation. */
    private static final class ClientGoneException extends RuntimeException {

        ClientGoneException(Throwable cause) {
            super(cause);
        }
    }
}
