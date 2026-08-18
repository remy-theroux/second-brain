package xyz.sterenn.secondbrain.users.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Durée de validité d'un jeton d'accès. C'est une règle métier, pas un réglage
 * d'exploitation : une propriété de configuration permettrait de la porter à trente jours
 * par un fichier, ce qui changerait la sécurité du produit sans passer par une revue.
 *
 * <p>Une heure, sans jeton de rafraîchissement : à l'expiration, on se reconnecte.
 * Pendant fonctionnel de {@code VerificationToken.VALIDITY}.
 */
public final class AccessTokenPolicy {

    /** Assez long pour une session de travail, assez court pour qu'un jeton volé se périme. */
    public static final Duration LIFETIME = Duration.ofHours(1);

    private AccessTokenPolicy() {
        // classe utilitaire
    }

    /**
     * @param maintenant instant d'émission, fourni par l'appelant — le domaine n'appelle
     *        jamais {@code Instant.now()} lui-même
     */
    public static Instant expiresAt(Instant maintenant) {
        return maintenant.plus(LIFETIME);
    }
}
