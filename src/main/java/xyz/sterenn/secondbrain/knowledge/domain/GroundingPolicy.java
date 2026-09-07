package xyz.sterenn.secondbrain.knowledge.domain;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Answer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

public final class GroundingPolicy {

    private GroundingPolicy() {}

    public static Answer verdict(Agent agent, String text, SourceCatalogue catalogue, boolean searchPerformed) {
        if (!searchPerformed) {
            return new Answer(text, List.of(), AnswerVerdict.CONVERSATIONAL);
        }
        List<Source> cited = catalogue.cited(CitationPolicy.citations(text));
        if (cited.isEmpty()) {
            return new Answer(agent.refusals().notFound(), List.of(), AnswerVerdict.UNGROUNDED);
        }
        return new Answer(text, cited, AnswerVerdict.GROUNDED);
    }

    public static Answer budgetExceeded(Agent agent) {
        return new Answer(agent.refusals().notFound(), List.of(), AnswerVerdict.BUDGET_EXCEEDED);
    }
}
