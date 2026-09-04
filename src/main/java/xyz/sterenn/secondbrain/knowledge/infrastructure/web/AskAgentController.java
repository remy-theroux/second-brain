package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
     * Au-dessus du budget d'exécution de l'agent : un emitter qui expire pendant que le serveur
     * travaille encore couperait le client sans rien lui dire.
     */
    private static final long TIMEOUT_MILLIS = 150_000L;

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
        Question question = conversationAgent.valide(request.question());
        UUID ownerId = JwtSubject.accountId(jwt);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        conversationExecutor.execute(() -> conduire(emitter, question, ownerId));
        return emitter;
    }

    private void conduire(SseEmitter emitter, Question question, UUID ownerId) {
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
        } catch (ClientPartiException clientParti) {
            LOG.info("Le client a fermé sa connexion : la génération est interrompue.");
            emitter.complete();
        } catch (LlmUnavailableException | EmbeddingUnavailableException serviceInjoignable) {
            LOG.error("La conversation a échoué : un service d'IA n'a pas répondu.", serviceInjoignable);
            echouer(
                    emitter,
                    "La conversation est momentanément indisponible : un service d'IA n'a pas "
                            + "répondu. Réessayez dans quelques instants.");
        } catch (RuntimeException echec) {
            LOG.error("La conversation a échoué.", echec);
            echouer(emitter, "La conversation a échoué. Réessayez dans quelques instants.");
        }
    }

    /** La trace est écrite APRÈS la fermeture du flux : son échec ne doit rien coûter à une réponse déjà livrée. */
    private void tracer(Question question, UUID ownerId, ConversationOutcome resultat) {
        try {
            commandBus.dispatch(new RecordAgentRun(
                    ownerId,
                    conversationAgent.nomDeLAgent(),
                    conversationAgent.versionDeLAgent(),
                    question.value(),
                    resultat.answer().text(),
                    resultat.answer().verdict(),
                    resultat.tours(),
                    resultat.duree().toMillis(),
                    resultat.recherches(),
                    resultat.answer().sources()));
        } catch (RuntimeException tracePerdue) {
            LOG.error("La trace de cette conversation n'a pas pu être écrite.", tracePerdue);
        }
    }

    private void echouer(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(new ErrorResponse(message)));
        } catch (IOException | IllegalStateException clientDejaParti) {
            LOG.info("Le client était déjà parti quand l'erreur a été émise.");
        }
        emitter.complete();
    }

    private void emettre(SseEmitter emitter, String evenement, Object donnees) {
        try {
            emitter.send(SseEmitter.event().name(evenement).data(donnees));
        } catch (IOException | IllegalStateException clientParti) {
            throw new ClientPartiException(clientParti);
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
    private static final class ClientPartiException extends RuntimeException {

        ClientPartiException(Throwable cause) {
            super(cause);
        }
    }
}
