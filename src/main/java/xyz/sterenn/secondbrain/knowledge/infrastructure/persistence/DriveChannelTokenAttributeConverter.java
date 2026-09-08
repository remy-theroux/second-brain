package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;

/** Same dispositif, same key and same deviation as {@link RefreshTokenAttributeConverter}. */
@Component
@Converter(autoApply = true)
public class DriveChannelTokenAttributeConverter implements AttributeConverter<DriveChannelToken, String> {

    private static final String WHAT = "a Drive channel token";

    private final TokenCipher tokenCipher;

    public DriveChannelTokenAttributeConverter(TokenCipher tokenCipher) {
        this.tokenCipher = tokenCipher;
    }

    @Override
    public String convertToDatabaseColumn(DriveChannelToken attribute) {
        return attribute == null ? null : tokenCipher.encipher(attribute.value(), WHAT);
    }

    @Override
    public DriveChannelToken convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new DriveChannelToken(tokenCipher.decipher(dbData, WHAT));
    }
}
