package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

// No line of code references this converter: autoApply applies it to every Email attribute,
// and Hibernate only finds it through the package scan. Removing it because it looks unused
// makes startup fail.
@Converter(autoApply = true)
public class EmailAttributeConverter implements AttributeConverter<Email, String> {

    @Override
    public String convertToDatabaseColumn(Email attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Email convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Email(dbData);
    }
}
