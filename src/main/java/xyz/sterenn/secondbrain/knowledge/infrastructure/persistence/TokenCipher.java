package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM under one key, shared by the converters of the Drive secrets. It never takes a
 * value into an exception message: the value is the clear secret.
 */
@Component
public class TokenCipher {

    /** AES-256. */
    public static final int KEY_LENGTH = 32;

    static final String PROPERTY = "secondbrain.drive.token-encryption-key";

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public TokenCipher(@Value("${" + PROPERTY + "}") String base64Key) {
        byte[] bytes = decodeKey(base64Key);
        if (bytes.length != KEY_LENGTH) {
            throw new IllegalStateException(PROPERTY + " must carry " + KEY_LENGTH
                    + " bytes encoded in Base64 to encipher in AES-256-GCM; " + bytes.length + " received");
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    private static byte[] decodeKey(String base64Key) {
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(PROPERTY + " is not valid Base64", e);
        }
    }

    String encipher(String clear, String what) {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] enciphered = cipher.doFinal(clear.getBytes(StandardCharsets.UTF_8));
            byte[] column = new byte[iv.length + enciphered.length];
            System.arraycopy(iv, 0, column, 0, iv.length);
            System.arraycopy(enciphered, 0, column, iv.length, enciphered.length);
            return Base64.getEncoder().encodeToString(column);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Enciphering " + what + " failed", e);
        }
    }

    String decipher(String column, String what) {
        byte[] bytes = Base64.getDecoder().decode(column);
        if (bytes.length <= IV_LENGTH) {
            throw new IllegalStateException("A ciphered " + what + " is shorter than its own IV");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH));
            byte[] clear = cipher.doFinal(bytes, IV_LENGTH, bytes.length - IV_LENGTH);
            return new String(clear, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Deciphering " + what + " failed", e);
        }
    }
}
