package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record Answer(String text, List<Source> sources, AnswerVerdict verdict) {

    public Answer {
        Objects.requireNonNull(text, "The answer text is required");
        Objects.requireNonNull(sources, "Sources are required, empty when there are none");
        Objects.requireNonNull(verdict, "A verdict is required");
        sources = List.copyOf(sources);
    }
}
