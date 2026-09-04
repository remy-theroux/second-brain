package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record Answer(String text, List<Source> sources, AnswerVerdict verdict) {

    public Answer {
        Objects.requireNonNull(text, "Le texte de la réponse est obligatoire");
        Objects.requireNonNull(sources, "Les sources sont obligatoires, vides s'il n'y en a pas");
        Objects.requireNonNull(verdict, "Le verdict est obligatoire");
        sources = List.copyOf(sources);
    }
}
