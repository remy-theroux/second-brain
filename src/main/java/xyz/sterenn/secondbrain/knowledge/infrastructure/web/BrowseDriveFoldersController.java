package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDriveConnectionExpired;
import xyz.sterenn.secondbrain.knowledge.application.query.BrowseDriveFolders;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.shared.web.ErrorResponse;

@RestController
public class BrowseDriveFoldersController {

    private final QueryBus queryBus;
    private final CommandBus commandBus;

    public BrowseDriveFoldersController(QueryBus queryBus, CommandBus commandBus) {
        this.queryBus = queryBus;
        this.commandBus = commandBus;
    }

    @GetMapping("/api/drive/folders")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<Object> browse(
            @RequestParam(name = "parent", defaultValue = "") String parent, @AuthenticationPrincipal Jwt jwt) {
        UUID ownerId = JwtSubject.accountId(jwt);
        try {
            return ResponseEntity.ok(queryBus.ask(new BrowseDriveFolders(ownerId, parent)));
        } catch (DriveNotConnectedException notConnected) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(notConnected.getMessage()));
        } catch (DriveAuthorizationRevokedException revoked) {
            // The browsing transaction is rolled back: the status needs a second one. See ADR-0028.
            commandBus.dispatch(new MarkDriveConnectionExpired(ownerId));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(revoked.getMessage()));
        } catch (GoogleDriveUnavailableException unavailable) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(unavailable.getMessage()));
        }
    }

    @ExceptionHandler(JwtSubject.UnreadableSubjectException.class)
    public ResponseEntity<Object> unreadableSubject() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
