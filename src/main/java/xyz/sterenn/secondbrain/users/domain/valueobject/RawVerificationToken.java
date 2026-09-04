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

    /** Base64url sans remplissage : le jeton voyage dans une URL sans échappement. */
    public static RawVerificationToken generate() {
        byte[] octets = new byte[BYTE_LENGTH];
        RANDOM.nextBytes(octets);
        return new RawVerificationToken(Base64.getUrlEncoder().withoutPadding().encodeToString(octets));
    }

    /** Masque la valeur : seule son empreinte salée est persistée, le clair ne doit pas fuir dans un log. */
    @Override
    public String toString() {
        return "RawVerificationToken[value=***]";
    }
}
