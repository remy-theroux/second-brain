package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.knowledge.application.command.CompleteDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidDriveAuthorizationException;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;

/** Outside {@code /api}: the link comes from Google, hence without a token in a header. */
@RestController
public class CompleteDriveAuthorizationController {

    private static final String DOCUMENTS_PATH = "/documents?drive=";

    private static final String ACCESS_DENIED = "access_denied";

    private final CommandBus commandBus;

    public CompleteDriveAuthorizationController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping("/drive/callback")
    public ResponseEntity<Void> complete(
            @RequestParam(name = "state", defaultValue = "") String state,
            @RequestParam(name = "code", defaultValue = "") String authorizationCode,
            @RequestParam(name = "error", defaultValue = "") String error) {
        // Google announces a refusal by this parameter, never by a missing code.
        if (!error.isBlank()) {
            return redirectTo(ACCESS_DENIED.equals(error) ? "refus" : "echec");
        }

        String outcome;
        try {
            commandBus.dispatch(new CompleteDriveAuthorization(state, authorizationCode));
            outcome = "ok";
        } catch (InvalidDriveAuthorizationException e) {
            // Unreadable state, unknown request, wrong state: one code as there is one message,
            // telling them apart would make this route an oracle.
            outcome = "lien-invalide";
        } catch (GoogleDriveUnavailableException e) {
            outcome = "echec";
        }
        return redirectTo(outcome);
    }

    /** Relative, as {@code GET /verification}: the browser resolves it against the origin. */
    private static ResponseEntity<Void> redirectTo(String outcome) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(DOCUMENTS_PATH + outcome))
                .build();
    }
}
