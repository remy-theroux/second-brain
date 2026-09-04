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
