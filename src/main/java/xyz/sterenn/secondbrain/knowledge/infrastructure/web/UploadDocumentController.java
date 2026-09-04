package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import xyz.sterenn.secondbrain.knowledge.application.command.UploadDocument;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnsupportedDocumentFormatException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;
import xyz.sterenn.secondbrain.shared.web.ValidationErrorResponse;

@RestController
public class UploadDocumentController {

    private final CommandBus commandBus;

    public UploadDocumentController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/api/documents")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> upload(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
        if (file.isEmpty()) {
            // UNPROCESSABLE_CONTENT et non UNPROCESSABLE_ENTITY : RFC 9110 a renommé le 422,
            // et Spring 7 a déprécié l'ancien nom. Même code, même corps.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(new ValidationErrorResponse(Map.of("file", "Le fichier est obligatoire.")));
        }

        byte[] contenu;
        try {
            contenu = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            commandBus.dispatch(new UploadDocument(JwtSubject.accountId(jwt), file.getOriginalFilename(), contenu));
        } catch (UnsupportedDocumentFormatException e) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse(e.getMessage()));
        } catch (DuplicateDocumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new DuplicateDocumentResponse(e.getMessage(), e.getExistingDocumentId()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Ne voit l'exception que parce que {@code spring.servlet.multipart.resolve-lazily} est à
     * {@code true} : sinon le multipart est résolu par {@code DispatcherServlet} avant qu'un
     * contrôleur soit choisi, et seul un {@code @RestControllerAdvice} global la capterait.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> tropVolumineux() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("Ce fichier dépasse la taille maximale acceptée."));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> sujetIllisible() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
