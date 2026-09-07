package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Answer;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AnswerVerdict;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

class GroundingPolicyTest {

    private static final Agent AGENT = TestAgents.anAgent();

    private static SourceCatalogue threeChunks() {
        UUID document = UUID.randomUUID();
        return SourceCatalogue.empty()
                .absorb(List.of(
                        new SourceCandidate(document, "a.pdf", 0, "", "un"),
                        new SourceCandidate(document, "a.pdf", 1, "", "deux"),
                        new SourceCandidate(document, "a.pdf", 2, "", "trois")))
                .catalogue();
    }

    @Test
    void lets_through_an_answer_that_did_not_search() {
        Answer answer = GroundingPolicy.verdict(AGENT, "Bonjour, je vous écoute.", SourceCatalogue.empty(), false);

        assertThat(answer.verdict()).isEqualTo(AnswerVerdict.CONVERSATIONAL);
        assertThat(answer.text()).isEqualTo("Bonjour, je vous écoute.");
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    void keeps_a_cited_answer_and_only_the_cited_chunks() {
        Answer answer = GroundingPolicy.verdict(AGENT, "Quatorze jours [3] et [1].", threeChunks(), true);

        assertThat(answer.verdict()).isEqualTo(AnswerVerdict.GROUNDED);
        assertThat(answer.text()).isEqualTo("Quatorze jours [3] et [1].");
        assertThat(answer.sources()).extracting(Source::number).containsExactly(3, 1);
    }

    @Test
    void replaces_an_uncited_answer_with_the_admission_of_ignorance() {
        Answer answer = GroundingPolicy.verdict(AGENT, "Canberra est la capitale.", threeChunks(), true);

        assertThat(answer.verdict()).isEqualTo(AnswerVerdict.UNGROUNDED);
        assertThat(answer.text()).isEqualTo(TestAgents.NOT_FOUND);
        assertThat(answer.sources()).isEmpty();
    }

    @Test
    void does_not_take_a_citation_outside_the_catalogue_as_grounding() {
        Answer answer = GroundingPolicy.verdict(AGENT, "Canberra [9] est la capitale.", threeChunks(), true);

        assertThat(answer.verdict()).isEqualTo(AnswerVerdict.UNGROUNDED);
        assertThat(answer.text()).isEqualTo(TestAgents.NOT_FOUND);
    }

    @Test
    void ignores_the_phantom_citation_but_keeps_the_answer_if_another_one_is_valid() {
        Answer answer = GroundingPolicy.verdict(AGENT, "Faux [9] mais vrai [2].", threeChunks(), true);

        assertThat(answer.verdict()).isEqualTo(AnswerVerdict.GROUNDED);
        assertThat(answer.text()).isEqualTo("Faux [9] mais vrai [2].");
        assertThat(answer.sources()).extracting(Source::number).containsExactly(2);
    }

    @Test
    void never_alters_the_text_it_keeps() {
        String asIs = "  Quatorze jours [1].  ";

        assertThat(GroundingPolicy.verdict(AGENT, asIs, threeChunks(), true).text())
                .isEqualTo(asIs);
    }

    @Test
    void returns_the_admission_when_the_budget_is_exhausted() {
        Answer answer = GroundingPolicy.budgetExceeded(AGENT);

        assertThat(answer.verdict()).isEqualTo(AnswerVerdict.BUDGET_EXCEEDED);
        assertThat(answer.text()).isEqualTo(TestAgents.NOT_FOUND);
    }
}
