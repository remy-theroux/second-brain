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

    private static final Agent AGENT = AgentDeTest.unAgent();

    private static SourceCatalogue troisExtraits() {
        UUID document = UUID.randomUUID();
        return SourceCatalogue.empty()
                .absorbe(List.of(
                        new SourceCandidate(document, "a.pdf", 0, "", "un"),
                        new SourceCandidate(document, "a.pdf", 1, "", "deux"),
                        new SourceCandidate(document, "a.pdf", 2, "", "trois")))
                .catalogue();
    }

    @Test
    void laisse_passer_une_reponse_sans_recherche() {
        Answer reponse = GroundingPolicy.verdict(AGENT, "Bonjour, je vous écoute.", SourceCatalogue.empty(), false);

        assertThat(reponse.verdict()).isEqualTo(AnswerVerdict.CONVERSATIONNELLE);
        assertThat(reponse.text()).isEqualTo("Bonjour, je vous écoute.");
        assertThat(reponse.sources()).isEmpty();
    }

    @Test
    void retient_une_reponse_citee_et_ne_garde_que_les_extraits_cites() {
        Answer reponse = GroundingPolicy.verdict(AGENT, "Quatorze jours [3] et [1].", troisExtraits(), true);

        assertThat(reponse.verdict()).isEqualTo(AnswerVerdict.SOURCEE);
        assertThat(reponse.text()).isEqualTo("Quatorze jours [3] et [1].");
        assertThat(reponse.sources()).extracting(Source::number).containsExactly(3, 1);
    }

    @Test
    void remplace_une_reponse_non_citee_par_l_aveu_d_ignorance() {
        Answer reponse = GroundingPolicy.verdict(AGENT, "Canberra est la capitale.", troisExtraits(), true);

        assertThat(reponse.verdict()).isEqualTo(AnswerVerdict.SANS_SOURCE);
        assertThat(reponse.text()).isEqualTo(AgentDeTest.AVEU);
        assertThat(reponse.sources()).isEmpty();
    }

    @Test
    void ne_tient_pas_une_citation_hors_catalogue_pour_un_ancrage() {
        Answer reponse = GroundingPolicy.verdict(AGENT, "Canberra [9] est la capitale.", troisExtraits(), true);

        assertThat(reponse.verdict()).isEqualTo(AnswerVerdict.SANS_SOURCE);
        assertThat(reponse.text()).isEqualTo(AgentDeTest.AVEU);
    }

    @Test
    void ignore_la_citation_fantome_mais_retient_la_reponse_si_une_autre_est_valide() {
        Answer reponse = GroundingPolicy.verdict(AGENT, "Faux [9] mais vrai [2].", troisExtraits(), true);

        assertThat(reponse.verdict()).isEqualTo(AnswerVerdict.SOURCEE);
        assertThat(reponse.text()).isEqualTo("Faux [9] mais vrai [2].");
        assertThat(reponse.sources()).extracting(Source::number).containsExactly(2);
    }

    @Test
    void ne_retouche_jamais_le_texte_qu_il_retient() {
        String tel_quel = "  Quatorze jours [1].  ";

        assertThat(GroundingPolicy.verdict(AGENT, tel_quel, troisExtraits(), true)
                        .text())
                .isEqualTo(tel_quel);
    }

    @Test
    void rend_l_aveu_quand_le_budget_est_epuise() {
        Answer reponse = GroundingPolicy.budgetDepasse(AGENT);

        assertThat(reponse.verdict()).isEqualTo(AnswerVerdict.BUDGET_DEPASSE);
        assertThat(reponse.text()).isEqualTo(AgentDeTest.AVEU);
    }
}
