package xyz.sterenn.secondbrain.users.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Règle de robustesse du mot de passe, alignée sur NIST SP 800-63B : une longueur
 * minimale et un refus des mots de passe les plus courants, mais aucune règle de
 * composition (majuscule / chiffre / caractère spécial), qui pousse les utilisateurs
 * vers des variantes prévisibles sans gain réel.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    /**
     * Mots de passe refusés d'office, en minuscules. Volontairement courte : une
     * blocklist sérieuse (type Have I Been Pwned) fera l'objet d'un ticket dédié.
     * N'y mettre que des entrées d'au moins {@value #MIN_LENGTH} caractères — en deçà,
     * le contrôle de longueur les rejette déjà.
     */
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
        "secondbrain1"
    );

    private PasswordPolicy() {
        // classe utilitaire
    }

    /**
     * @param rawPassword mot de passe en clair, éventuellement {@code null}
     * @return {@code true} si le mot de passe satisfait la politique
     */
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
