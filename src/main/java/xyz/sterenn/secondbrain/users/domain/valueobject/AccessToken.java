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

    /** Never negative: this is RFC 6749's {@code expires_in}, whose integer must be positive. */
    public long expiresIn(Instant now) {
        return Math.max(Duration.between(now, expiresAt).toSeconds(), 0L);
    }

    /** Masks the value: whoever holds it is that user. */
    @Override
    public String toString() {
        return "AccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
