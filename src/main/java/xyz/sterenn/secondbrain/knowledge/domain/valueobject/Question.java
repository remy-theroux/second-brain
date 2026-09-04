package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidQuestionException;

public record Question(String value) {

    public Question {
        if (value == null || value.isBlank()) {
            throw new InvalidQuestionException("La question ne peut pas être vide.");
        }
        value = value.strip();
    }
}
