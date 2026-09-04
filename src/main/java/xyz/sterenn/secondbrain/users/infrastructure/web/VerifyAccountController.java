package xyz.sterenn.secondbrain.users.infrastructure.web;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.application.command.VerifyAccount;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;

@RestController
public class VerifyAccountController {

    private static final String LOGIN_PATH = "/login?verification=";

    private final CommandBus commandBus;

    public VerifyAccountController(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @GetMapping("/verification")
    public ResponseEntity<Void> verify(
            @RequestParam(name = "compte", defaultValue = "") String compte,
            @RequestParam(name = "jeton", defaultValue = "") String jeton) {
        String code;
        try {
            commandBus.dispatch(new VerifyAccount(compte, jeton));
            code = "ok";
        } catch (InvalidVerificationLinkException e) {
            // UUID illisible, compte inconnu, jeton faux : un seul code comme un seul message,
            // les distinguer ferait de cette route un oracle d'existence de compte.
            code = "lien-invalide";
        } catch (ExpiredVerificationLinkException e) {
            code = "lien-expire";
        } catch (AlreadyUsedVerificationLinkException e) {
            code = "lien-deja-utilise";
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(LOGIN_PATH + code))
                .build();
    }
}
