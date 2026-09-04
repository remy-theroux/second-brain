package xyz.sterenn.secondbrain.users.domain;

import java.util.Locale;
import java.util.Set;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;

    // BCrypt ne lit que les 72 premiers octets : au-delà, deux mots de passe partageant
    // ce préfixe ouvrent le même compte — voir ADR-0005.
    public static final int MAX_LENGTH = 128;

    // N'y mettre que des entrées d'au moins MIN_LENGTH caractères : en deçà, le contrôle
    // de longueur les rejette déjà.
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
