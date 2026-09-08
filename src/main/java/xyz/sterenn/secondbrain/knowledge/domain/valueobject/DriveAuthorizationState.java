package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * The anti-CSRF nonce of an authorization request. Not a secret: knowing it grants nothing,
 * so it is persisted in the clear — see the verification token for the opposite case.
 */
public record DriveAuthorizationState(String value) {

    public static final int BYTE_LENGTH = 32;

    public static final int MAX_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();

    public DriveAuthorizationState {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The authorization state is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "An authorization state is at most " + MAX_LENGTH + " characters, got: " + value.length());
        }
    }

    /** Base64url without padding: the nonce travels in a URL without escaping. */
    public static DriveAuthorizationState random() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return new DriveAuthorizationState(
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    @Override
    public String toString() {
        return value;
    }
}
