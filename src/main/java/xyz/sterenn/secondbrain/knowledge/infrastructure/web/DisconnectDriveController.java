package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.DisconnectDrive;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveConnectionNotFoundException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

@RestController
public class DisconnectDriveController {

    private final CommandBus commandBus;

    public DisconnectDriveController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @DeleteMapping("/api/drive/connection")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> disconnect(@AuthenticationPrincipal Jwt jwt) {
        try {
            commandBus.dispatch(new DisconnectDrive(JwtSubject.accountId(jwt)));
        } catch (DriveConnectionNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
        }
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
