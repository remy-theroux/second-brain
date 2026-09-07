package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.security.SecureRandom;
import java.util.Base64;

public record RawVerificationToken(String value) {

    public static final int BYTE_LENGTH = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    public RawVerificationToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le jeton de vérification est obligatoire");
        }
    }

    /** Base64url without padding: the token travels in a URL without escaping. */
    public static RawVerificationToken generate() {
        byte[] bytes = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return new RawVerificationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** Masks the value: only its salted digest is persisted, the clear text must not leak into a log. */
    @Override
    public String toString() {
        return "RawVerificationToken[value=***]";
    }
}
