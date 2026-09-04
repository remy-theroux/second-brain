package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

public record Checksum(String value) {

    public static final int LENGTH = 64;

    private static final String ALGORITHM = "SHA-256";
    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{" + LENGTH + "}$");

    public Checksum {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("L'empreinte du contenu est obligatoire");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Une empreinte SHA-256 s'écrit en " + LENGTH
                    + " caractères hexadécimaux, reçu : " + value.length());
        }
    }

    public static Checksum of(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("Le contenu est obligatoire");
        }
        try {
            return new Checksum(HexFormat.of()
                    .formatHex(MessageDigest.getInstance(ALGORITHM).digest(content)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est exigé de toute implémentation de la plateforme Java : hors d'atteinte.
            throw new IllegalStateException("Algorithme " + ALGORITHM + " indisponible", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
