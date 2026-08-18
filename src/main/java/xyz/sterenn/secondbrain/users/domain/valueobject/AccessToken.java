package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.time.Duration;
import java.time.Instant;

/**
 * Jeton d'accès délivré à un utilisateur authentifié, et l'instant où il cesse de valoir.
 *
 * <p>Le domaine ignore que c'est un JWT : pour lui, c'est une chaîne opaque que son
 * porteur présentera pour être reconnu. Le format est l'affaire de l'adapter.
 *
 * <p>{@link #toString()} est masqué : quiconque détient cette valeur est cet utilisateur,
 * elle n'a donc rien à faire dans un log ni dans un message d'échec d'assertion.
 */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le jeton d'accès est obligatoire");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("L'expiration du jeton d'accès est obligatoire");
        }
    }

    /**
     * Secondes restantes avant expiration, jamais négatives : c'est le {@code expires_in}
     * de la réponse HTTP, dont RFC 6749 attend un entier positif.
     */
    public long expiresIn(Instant maintenant) {
        return Math.max(Duration.between(maintenant, expiresAt).toSeconds(), 0L);
    }

    @Override
    public String toString() {
        return "AccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
