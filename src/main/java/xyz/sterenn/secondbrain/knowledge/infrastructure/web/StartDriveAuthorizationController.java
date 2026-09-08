package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.StartDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

@RestController
public class StartDriveAuthorizationController {

    private final CommandBus commandBus;
    private final GoogleDriveAuthorization googleDriveAuthorization;

    public StartDriveAuthorizationController(CommandBus commandBus, GoogleDriveAuthorization googleDriveAuthorization) {
        this.commandBus = commandBus;
        this.googleDriveAuthorization = googleDriveAuthorization;
    }

    /**
     * The nonce is drawn here because a command returns nothing: obtaining the consent URL from
     * the handler would mean re-reading what it just wrote.
     */
    @PostMapping("/api/drive/authorizations")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> start(@AuthenticationPrincipal Jwt jwt) {
        DriveAuthorizationState state = DriveAuthorizationState.random();

        commandBus.dispatch(new StartDriveAuthorization(JwtSubject.accountId(jwt), state));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DriveAuthorizationResponse(
                        googleDriveAuthorization.consentUrl(state).toString()));
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
