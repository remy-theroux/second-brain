package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;
import xyz.sterenn.secondbrain.shared.web.ValidationErrorResponse;

@RestController
public class SearchChunksController {

    private final QueryBus queryBus;

    public SearchChunksController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    // defaultValue = "" : un paramètre absent suit le chemin d'un paramètre vide, donc un seul
    // refus à écrire.
    @GetMapping("/api/search")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> search(
            @RequestParam(name = "q", defaultValue = "") String question, @AuthenticationPrincipal Jwt jwt) {
        try {
            return ResponseEntity.ok(queryBus.ask(new SearchChunks(question, JwtSubject.accountId(jwt))));
        } catch (InvalidQuestionException questionIllisible) {
            return ResponseEntity.unprocessableEntity()
                    .body(new ValidationErrorResponse(Map.of("q", questionIllisible.getMessage())));
        } catch (EmbeddingUnavailableException vectorisationInjoignable) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("La recherche est momentanément indisponible : le service de "
                            + "vectorisation n'a pas répondu. Réessayez dans quelques instants."));
        }
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> sujetIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
