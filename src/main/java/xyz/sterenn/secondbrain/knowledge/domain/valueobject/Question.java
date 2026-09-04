package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;

public record Question(String value) {

    public static final int LONGUEUR_MAXIMALE = 2000;

    public Question {
        if (value == null || value.isBlank()) {
            throw new InvalidQuestionException("La question ne peut pas être vide.");
        }
        value = value.strip();
        if (value.length() > LONGUEUR_MAXIMALE) {
            throw new InvalidQuestionException("La question ne peut pas dépasser 2 000 caractères.");
        }
    }
}
