package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Jeton de vérification en clair. Il n'existe qu'à deux endroits : dans la notification
 * envoyée à l'utilisateur, et le temps du calcul de son empreinte. Ce qui est persisté,
 * c'est uniquement son hash salé.
 *
 * <p>{@link #toString()} est redéfini pour qu'un log ou un message d'échec d'assertion ne
 * puisse jamais le rendre en clair — même règle que les commandes portant un secret.
 */
public record RawVerificationToken(String value) {

    /** 32 octets d'entropie : hors de portée d'une recherche exhaustive. */
    public static final int BYTE_LENGTH = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    public RawVerificationToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Le jeton de vérification est obligatoire");
        }
    }

    /**
     * Tire un jeton aléatoire, encodé en base64url sans remplissage : les 43 caractères
     * obtenus traversent une URL sans échappement.
     */
    public static RawVerificationToken generate() {
        byte[] octets = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(octets);
        return new RawVerificationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
    }

    @Override
    public String toString() {
        return "RawVerificationToken[value=***]";
    }
}
