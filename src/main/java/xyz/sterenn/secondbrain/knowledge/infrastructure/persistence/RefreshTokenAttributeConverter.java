package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
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
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

/**
 * Deviation from the "no @Component on a converter" rule of backend.md: the key cannot be a
 * constant, so Hibernate resolves this converter through Spring's BeanContainer instead of
 * instantiating it itself. It still needs {@code autoApply} to be found by the entity scan.
 */
@Component
@Converter(autoApply = true)
public class RefreshTokenAttributeConverter implements AttributeConverter<RefreshToken, String> {

    /** AES-256. */
    public static final int KEY_LENGTH = 32;

    private static final String PROPERTY = "secondbrain.drive.token-encryption-key";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public RefreshTokenAttributeConverter(@Value("${" + PROPERTY + "}") String base64Key) {
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

    @Override
    public String convertToDatabaseColumn(RefreshToken attribute) {
        if (attribute == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] enciphered = cipher.doFinal(attribute.value().getBytes(StandardCharsets.UTF_8));
            byte[] column = new byte[iv.length + enciphered.length];
            System.arraycopy(iv, 0, column, 0, iv.length);
            System.arraycopy(enciphered, 0, column, iv.length, enciphered.length);
            return Base64.getEncoder().encodeToString(column);
        } catch (GeneralSecurityException e) {
            // Never the value in the message: it is the clear token.
            throw new IllegalStateException("Enciphering a Drive refresh token failed", e);
        }
    }

    @Override
    public RefreshToken convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        byte[] column = Base64.getDecoder().decode(dbData);
        if (column.length <= IV_LENGTH) {
            throw new IllegalStateException("A ciphered Drive refresh token is shorter than its own IV");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, column, 0, IV_LENGTH));
            byte[] clear = cipher.doFinal(column, IV_LENGTH, column.length - IV_LENGTH);
            return new RefreshToken(new String(clear, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Deciphering a Drive refresh token failed", e);
        }
    }
}
