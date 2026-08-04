package xyz.sterenn.secondbrain.users.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Adresse email d'un compte, toujours normalisée : impossible de construire un
 * {@code Email} invalide, et deux écritures d'une même adresse sont égales.
 *
 * <p>La validation reste volontairement grossière : une expression régulière ne
 * décide pas de la validité réelle d'une adresse. La confirmation par email, prévue
 * dans un ticket dédié, est le seul contrôle qui compte.
 */
public record Email(String value) {

    /** 64 (partie locale) + 1 (@) + 255 (domaine), maximum de la RFC 5321. */
    public static final int MAX_LENGTH = 320;

    private static final Pattern FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new InvalidEmailException("L'email est obligatoire");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.length() > MAX_LENGTH) {
            throw new InvalidEmailException("L'email ne peut pas dépasser " + MAX_LENGTH + " caractères");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new InvalidEmailException("Format d'email invalide");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
