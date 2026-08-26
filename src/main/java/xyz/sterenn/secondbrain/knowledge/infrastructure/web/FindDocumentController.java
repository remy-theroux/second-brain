package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.FindDocument;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

/**
 * Adapter entrant du détail d'un document : ce qu'il est, et ce qui en a été extrait.
 *
 * <p><strong>Une seule requête pour tout l'écran</strong> — le nom, le statut, le motif
 * d'échec et les blocs. Une route {@code /extraction} séparée aurait rendu {@code 404} sur un
 * document en attente, ce qui se lit « ce document n'existe pas » alors qu'il est simplement
 * en file.
 *
 * <p>Le vide de l'{@link java.util.Optional} devient {@code 404} ici, et nulle part ailleurs :
 * la query ne lève pas. Le message est celui de {@link DocumentNotFoundException#MESSAGE} — le
 * même que rend la suppression, parce que c'est le même refus.
 */
@RestController
public class FindDocumentController {

    private final QueryBus queryBus;

    public FindDocumentController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/documents/{id}")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> find(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return queryBus.ask(new FindDocument(id, JwtSubject.accountId(jwt)))
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(DocumentNotFoundException.MESSAGE)));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> sujetIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
