package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

/** What Google hands back once the authorization code has been exchanged. */
public record DriveGrant(String googleEmail, RefreshToken refreshToken) {

    public DriveGrant {
        if (googleEmail == null || googleEmail.isBlank()) {
            throw new IllegalArgumentException("The Google address of the authorizing account is required");
        }
        if (refreshToken == null) {
            throw new IllegalArgumentException("A grant without a refresh token dies at the next restart");
        }
        googleEmail = googleEmail.trim();
    }
}
