package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.time.Duration;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Answer;

public record ConversationOutcome(Answer answer, List<String> searches, int turns, Duration duration) {

    public ConversationOutcome {
        searches = List.copyOf(searches);
    }
}
