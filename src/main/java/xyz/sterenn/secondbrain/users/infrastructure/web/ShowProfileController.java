package xyz.sterenn.secondbrain.users.infrastructure.web;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.query.FindUserById;
import xyz.sterenn.secondbrain.users.application.query.UserView;

/**
 * Adapter entrant du profil du porteur du jeton. C'est cet appel qui répond à la question
 * « ma session tient-elle encore ? » : il n'existe pas d'autre façon pour le front de le
 * savoir, puisque le serveur ne garde aucun état.
 *
 * <p>Le jeton est déjà validé par le filtre resource server quand cette méthode s'exécute :
 * signature, expiration et forme ont été contrôlées en amont. Il ne reste qu'à lire
 * {@code sub}.
 *
 * <p>Un jeton bien signé dont le compte a disparu répond {@code 401} et non {@code 404} :
 * il n'identifie plus personne, et le front n'a ainsi qu'un seul cas d'échec à traiter.
 */
@RestController
public class ShowProfileController {

    private final QueryBus queryBus;

    public ShowProfileController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @GetMapping("/api/profile")
    @SecurityRequirement(name = "bearer")
    public ResponseEntity<UserView> showProfile(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId;
        try {
            accountId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Hors de portée avec nos propres jetons ; un `sub` illisible n'identifie personne.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return queryBus.ask(new FindUserById(accountId))
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }
}
