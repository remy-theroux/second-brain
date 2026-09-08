package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Duration;
import java.time.Instant;

/** The short-lived token Google hands back for a refresh token, and the instant it dies. */
public record DriveAccessToken(String value, Instant expiresAt) {

    /** A token about to die does not leave for a call that may outlive it. */
    public static final Duration EXPIRY_MARGIN = Duration.ofSeconds(60);

    public DriveAccessToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The Google access token is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("The expiry of an access token is required");
        }
    }

    public boolean isUsableAt(Instant now) {
        return now.isBefore(expiresAt.minus(EXPIRY_MARGIN));
    }

    /** Masks the value: it opens a Drive, it must not leak into a log. */
    @Override
    public String toString() {
        return "DriveAccessToken[value=***, expiresAt=" + expiresAt + "]";
    }
}
