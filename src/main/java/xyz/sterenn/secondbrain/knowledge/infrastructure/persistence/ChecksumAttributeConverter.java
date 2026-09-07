package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

/**
 * No line of code references this converter: {@code autoApply} applies it to every
 * {@link Checksum} attribute, and Hibernate only discovers it through the package scan.
 * Deleting it because it looks unused makes startup fail.
 */
@Converter(autoApply = true)
public class ChecksumAttributeConverter implements AttributeConverter<Checksum, String> {

    @Override
    public String convertToDatabaseColumn(Checksum attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public Checksum convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Checksum(dbData);
    }
}
