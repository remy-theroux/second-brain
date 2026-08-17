package xyz.sterenn.secondbrain.users.domain.port;

import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.valueobject.AccessToken;

/**
 * Port sortant vers l'émission d'un jeton d'accès. Le domaine dit <em>pour qui</em> et
 * <em>jusqu'à quand</em> ; l'adapter décide de la forme et de la signature.
 *
 * <p>Ni JWT, ni algorithme, ni revendication n'apparaissent dans cette signature : c'est
 * ce qui permettra de changer de format sans toucher au handler d'authentification.
 */
public interface AccessTokenIssuer {

    /**
     * @param subject   identifiant du compte que le jeton désignera
     * @param issuedAt  instant d'émission
     * @param expiresAt instant au-delà duquel le jeton ne vaut plus rien
     */
    AccessToken issue(UUID subject, Instant issuedAt, Instant expiresAt);
}
