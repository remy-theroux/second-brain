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

/**
 * Adapter entrant du dépôt d'un document. Aucune règle métier ne vit ici : le contrôleur
 * lit le multipart, dispatche, et traduit les refus du domaine en codes HTTP.
 *
 * <p>Le {@code 201} n'a ni corps ni en-tête {@code Location}, comme
 * {@code POST /api/registrations} : une commande ne retourne rien, et {@code GET
 * /api/documents} rend l'état complet de la base — c'est lui que l'appelant relit.
 *
 * <p>Trois refus, trois codes, parce qu'ils appellent trois gestes différents : changer de
 * fichier ({@code 415}), aller voir le document existant ({@code 409}), alléger son dépôt
 * ({@code 413}).
 */
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
            // Le corps de la requête s'est interrompu en cours de lecture : rien n'a été
            // dispatché, il n'y a donc rien à annuler.
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
     * Le dépassement du plafond de téléversement.
     *
     * <p>Ce {@code @ExceptionHandler} ne voit l'exception que parce que
     * {@code spring.servlet.multipart.resolve-lazily} est à {@code true} : sans lui, le
     * multipart est résolu par {@code DispatcherServlet} <em>avant</em> qu'un contrôleur
     * soit choisi, et seul un {@code @RestControllerAdvice} global la capterait — ce que ce
     * projet évite, pour que la traduction des refus reste auprès de la route concernée.
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
