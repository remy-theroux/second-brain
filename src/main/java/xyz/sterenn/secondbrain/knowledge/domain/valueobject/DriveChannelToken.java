package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The secret a notification carries back to prove it comes from a channel we opened. It is the
 * only thing that separates a legitimate notification from an anonymous call: Google does not
 * authenticate itself towards us.
 */
public record DriveChannelToken(String value) {

    public static final int BYTE_LENGTH = 32;

    public static final int MAX_LENGTH = 64;

    private static final SecureRandom RANDOM = new SecureRandom();

    public DriveChannelToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The Drive channel token is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "A Drive channel token is at most " + MAX_LENGTH + " characters, got: " + value.length());
        }
    }

    /** Base64url without padding: the token travels in a request body and in an HTTP header. */
    public static DriveChannelToken random() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return new DriveChannelToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** Constant time, so that an unauthenticated route does not answer faster on a longer match. */
    public boolean matches(DriveChannelToken candidate) {
        return candidate != null
                && MessageDigest.isEqual(
                        value.getBytes(StandardCharsets.UTF_8), candidate.value.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String toString() {
        return "DriveChannelToken[value=***]";
    }
}
