package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDriveConnectionExpired;
import xyz.sterenn.secondbrain.knowledge.application.command.WatchDriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveFolderNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.FolderAlreadyCoveredException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;
import xyz.sterenn.secondbrain.shared.web.ValidationErrorResponse;

@RestController
public class WatchDriveFolderController {

    private final CommandBus commandBus;

    public WatchDriveFolderController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @PostMapping("/api/drive/watched-folders")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> watch(
            @RequestBody WatchDriveFolderRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = JwtSubject.accountId(jwt);
        if (request.folderId() == null || request.folderId().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(new ValidationErrorResponse(Map.of("folderId", "Le dossier à surveiller est obligatoire.")));
        }

        try {
            commandBus.dispatch(new WatchDriveFolder(ownerId, request.folderId()));
        } catch (DriveNotConnectedException | FolderAlreadyCoveredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
        } catch (DriveFolderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        } catch (DriveAuthorizationRevokedException revoked) {
            // The watching transaction is rolled back: the status needs a second one. See ADR-0028.
            commandBus.dispatch(new MarkDriveConnectionExpired(ownerId));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(revoked.getMessage()));
        } catch (GoogleDriveUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
