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
            throw new IllegalArgumentException("A content checksum is required");
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "A SHA-256 checksum is written with " + LENGTH + " hexadecimal characters, got: " + value.length());
        }
    }

    public static Checksum of(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("Content is required");
        }
        try {
            return new Checksum(HexFormat.of()
                    .formatHex(MessageDigest.getInstance(ALGORITHM).digest(content)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every Java platform implementation: out of reach.
            throw new IllegalStateException("Algorithm " + ALGORITHM + " unavailable", e);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
