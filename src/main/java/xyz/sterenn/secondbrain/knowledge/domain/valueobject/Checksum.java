package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Empreinte SHA-256 d'un contenu, en 64 caractères hexadécimaux minuscules.
 *
 * <p>C'est <strong>elle</strong> qui identifie un document, jamais le nom du fichier : le
 * même contenu redéposé sous un autre nom reste le même document, et deux contenus
 * différents portant le même nom sont deux documents. Un nom se change d'un clic, un
 * contenu non.
 *
 * <p>{@code MessageDigest} vient du JDK, pas d'un framework : le domaine reste sans
 * dépendance, et le calcul se teste sans Spring contre des empreintes connues.
 */
public record Checksum(String value) {

    /** SHA-256 : 256 bits, donc 64 caractères hexadécimaux. */
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

    /**
     * Calcule l'empreinte d'un contenu.
     *
     * <p>Le tableau entier est exigé : SHA-256 ne se calcule pas sur un extrait. C'est ce
     * qui impose de tenir le fichier en mémoire, et donc ce que le plafond de téléversement
     * borne (voir ADR-0021).
     */
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
