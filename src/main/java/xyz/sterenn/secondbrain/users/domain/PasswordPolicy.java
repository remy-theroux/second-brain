package xyz.sterenn.secondbrain.users.domain;

import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    // BCrypt only reads the first 72 bytes: beyond that, two passwords sharing this
    // prefix open the same account — see ADR-0005.
    public static final int MAX_LENGTH = 128;

    // Only put entries of at least MIN_LENGTH characters here: below that, the length
    // check already rejects them.
    private static final Set<String> BLOCKLIST = Set.of(
            "password1234",
            "passwordpassword",
            "motdepasse12",
            "motdepasse123",
            "123456789012",
            "1234567890123",
            "azertyuiopqs",
            "qwertyuiopas",
            "administrator",
            "secondbrain1");

    private PasswordPolicy() {}

    public static boolean isAcceptable(String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            return false;
        }
        return !BLOCKLIST.contains(rawPassword.toLowerCase(Locale.ROOT));
    }
}
