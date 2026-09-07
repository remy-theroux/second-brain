package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.query.DocumentContentView;
import xyz.sterenn.secondbrain.knowledge.application.query.FindDocumentContent;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.MissingDocumentContentException;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

@RestController
public class FindDocumentContentController {

    private final QueryBus queryBus;

    public FindDocumentContentController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/documents/{id}/content")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> content(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        try {
            return queryBus.ask(new FindDocumentContent(id, JwtSubject.accountId(jwt)))
                    .<ResponseEntity<Object>>map(FindDocumentContentController::attachment)
                    .orElseGet(() -> notFound(DocumentNotFoundException.MESSAGE));
        } catch (MissingDocumentContentException goneOriginal) {
            return notFound(goneOriginal.getMessage());
        } catch (DocumentStorageUnavailableException unreachableStorage) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Le téléchargement est momentanément indisponible : le stockage des "
                            + "originaux n'a pas répondu. Réessayez dans quelques instants."));
        }
    }

    private static ResponseEntity<Object> attachment(DocumentContentView content) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.format().mediaType()))
                .contentLength(content.content().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(content.filename(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(content.content());
    }

    private static ResponseEntity<Object> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(message));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
