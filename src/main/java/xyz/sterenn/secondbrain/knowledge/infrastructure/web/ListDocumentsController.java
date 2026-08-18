package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.DocumentView;
import xyz.sterenn.secondbrain.knowledge.application.query.ListDocuments;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;

/**
 * Adapter entrant de la liste des documents. Elle ne rend que ceux du porteur du jeton :
 * la base de connaissance appartient à un compte, et le filtrage est porté par la query,
 * pas ajouté ici après coup.
 *
 * <p>Une base vide rend {@code 200} et une liste vide, jamais {@code 404} : c'est l'état
 * de tout compte qui n'a encore rien déposé.
 */
@RestController
public class ListDocumentsController {

    private final QueryBus queryBus;

    public ListDocumentsController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/documents")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<List<DocumentView>> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(queryBus.ask(new ListDocuments(JwtSubject.accountId(jwt))));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> sujetIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
