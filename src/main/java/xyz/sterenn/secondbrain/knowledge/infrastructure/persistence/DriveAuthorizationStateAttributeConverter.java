package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;

/**
 * Same status as ChecksumAttributeConverter: no line of code references it, {@code autoApply}
 * and the package scan are what apply it. Deleting it because it looks unused breaks startup.
 */
@Converter(autoApply = true)
public class DriveAuthorizationStateAttributeConverter implements AttributeConverter<DriveAuthorizationState, String> {

    @Override
    public String convertToDatabaseColumn(DriveAuthorizationState attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public DriveAuthorizationState convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new DriveAuthorizationState(dbData);
    }
}
