package xyz.sterenn.secondbrain.users.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Projette le value object {@link Email} sur une colonne texte. Placé dans le domaine
 * par cohérence avec {@link User}, qui porte déjà les annotations JPA (voir la note
 * « entité de domaine annotée JPA » du plan d'architecture).
 */
@Converter
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
