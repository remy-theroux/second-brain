package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.RequestDriveFolderImport;
import xyz.sterenn.secondbrain.knowledge.domain.exception.WatchedFolderNotFoundException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

@RestController
public class RequestDriveFolderImportController {

    private final CommandBus commandBus;

    public RequestDriveFolderImportController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    /** 202 and not 201: the walk has not even started when the answer leaves, and nothing was created. */
    @PostMapping("/api/drive/watched-folders/{id}/import")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> requestImport(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        try {
            commandBus.dispatch(new RequestDriveFolderImport(JwtSubject.accountId(jwt), id));
        } catch (WatchedFolderNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        }
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
