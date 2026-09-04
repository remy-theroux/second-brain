package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

// Aucune ligne de code ne référence ce converter : autoApply l'applique à tout attribut Email,
// et Hibernate ne le trouve que par le scan de packages. Le supprimer au motif qu'il paraît
// inutilisé fait échouer le démarrage.
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
