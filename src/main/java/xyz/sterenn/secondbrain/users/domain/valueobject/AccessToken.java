package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.time.Duration;
import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le jeton d'accès est obligatoire");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("L'expiration du jeton d'accès est obligatoire");
        }
    }

    /** Jamais négatif : c'est l'{@code expires_in} de RFC 6749, dont l'entier doit être positif. */
    public long expiresIn(Instant maintenant) {
        return Math.max(Duration.between(maintenant, expiresAt).toSeconds(), 0L);
    }

    /** Masque la valeur : quiconque la détient est cet utilisateur. */
    @Override
    public String toString() {
        return "AccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
