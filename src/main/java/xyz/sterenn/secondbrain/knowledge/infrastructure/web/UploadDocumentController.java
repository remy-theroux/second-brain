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
            // UNPROCESSABLE_CONTENT and not UNPROCESSABLE_ENTITY: RFC 9110 renamed the 422,
            // and Spring 7 deprecated the old name. Same code, same body.
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(new ValidationErrorResponse(Map.of("file", "Le fichier est obligatoire.")));
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        try {
            commandBus.dispatch(new UploadDocument(JwtSubject.accountId(jwt), file.getOriginalFilename(), content));
        } catch (UnsupportedDocumentFormatException e) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse(e.getMessage()));
        } catch (DuplicateDocumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new DuplicateDocumentResponse(e.getMessage(), e.getExistingDocumentId()));
        }

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Sees the exception only because {@code spring.servlet.multipart.resolve-lazily} is
     * {@code true}: otherwise the multipart is resolved by {@code DispatcherServlet} before a
     * controller is chosen, and only a global {@code @RestControllerAdvice} would catch it.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> tooLarge() {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(new ErrorResponse("Ce fichier dépasse la taille maximale acceptée."));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
