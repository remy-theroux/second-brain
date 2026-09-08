package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
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

    private static final String WHAT = "a Drive refresh token";

    private final TokenCipher tokenCipher;

    public RefreshTokenAttributeConverter(TokenCipher tokenCipher) {
        this.tokenCipher = tokenCipher;
    }

    @Override
    public String convertToDatabaseColumn(RefreshToken attribute) {
        return attribute == null ? null : tokenCipher.encipher(attribute.value(), WHAT);
    }

    @Override
    public RefreshToken convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new RefreshToken(tokenCipher.decipher(dbData, WHAT));
    }
}
