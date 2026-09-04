package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

/**
 * Aucune ligne de code ne référence ce converter : {@code autoApply} l'applique à tout
 * attribut {@link Checksum}, et Hibernate ne le découvre que par le scan de packages. Le
 * supprimer au motif qu'il paraît inutilisé fait échouer le démarrage.
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
