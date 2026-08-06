package xyz.sterenn.secondbrain.users.infrastructure.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * Projette le value object {@link Email} sur une colonne texte. Un mapping est un détail
 * de persistance, donc de l'infrastructure : le domaine ne nomme cette classe nulle part.
 *
 * <p><strong>Aucune ligne de code ne référence ce converter.</strong> {@code autoApply} le
 * fait appliquer à tout attribut de type {@link Email}, et Hibernate ne le connaît que
 * parce que le scan d'entités part du package de {@code SecondBrainApplication}. Le
 * supprimer au motif qu'il paraît inutilisé fait échouer le démarrage de l'application.
 */
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
