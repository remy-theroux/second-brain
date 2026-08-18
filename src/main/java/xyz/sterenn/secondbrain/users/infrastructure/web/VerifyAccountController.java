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

/**
 * Adapter entrant de la route de vérification. Elle est <strong>hors de {@code /api}</strong>
 * et le reste : le lien part par email, il doit fonctionner dans n'importe quel client mail,
 * sans JavaScript et sans que le front soit en ligne. C'est la seule action du back qui ne
 * soit pas derrière l'API.
 *
 * <p>Le résultat voyage en <em>code</em> et non en message : c'est le front qui porte la
 * rédaction. Faire voyager le message lui-même le collerait dans l'historique du navigateur
 * et dans les logs d'accès du proxy, comme le fait déjà le jeton, pour aucun gain.
 *
 * <p>L'en-tête {@code Location} est <strong>relatif</strong> : le navigateur le résout contre
 * l'origine de la requête. L'application n'a donc aucune URL de front à connaître, et
 * l'origine unique garantie par le reverse proxy suffit.
 *
 * <p>Les paramètres sont optionnels et vides par défaut : un lien tronqué doit donner le même
 * refus qu'un lien falsifié, pas une erreur 400.
 */
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
            @RequestParam(name = "jeton", defaultValue = "") String jeton
    ) {
        String code;
        try {
            commandBus.dispatch(new VerifyAccount(compte, jeton));
            code = "ok";
        } catch (InvalidVerificationLinkException e) {
            // Les trois causes — UUID illisible, compte inconnu, jeton faux — partagent ce
            // code comme elles partagent un seul message : les distinguer ferait de cette
            // route un oracle d'existence de compte.
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
