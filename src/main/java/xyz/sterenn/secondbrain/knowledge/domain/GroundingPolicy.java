package xyz.sterenn.secondbrain.knowledge.domain;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Answer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

public final class GroundingPolicy {

    private GroundingPolicy() {}

    public static Answer verdict(Agent agent, String texte, SourceCatalogue catalogue, boolean rechercheEffectuee) {
        if (!rechercheEffectuee) {
            return new Answer(texte, List.of(), AnswerVerdict.CONVERSATIONNELLE);
        }
        List<Source> citees = catalogue.citees(CitationPolicy.citations(texte));
        if (citees.isEmpty()) {
            return new Answer(agent.refusals().introuvable(), List.of(), AnswerVerdict.SANS_SOURCE);
        }
        return new Answer(texte, citees, AnswerVerdict.SOURCEE);
    }

    public static Answer budgetDepasse(Agent agent) {
        return new Answer(agent.refusals().introuvable(), List.of(), AnswerVerdict.BUDGET_DEPASSE);
    }
}
