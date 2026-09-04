package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AgentRefusals;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExecutionBudget;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

class DocumentAgentTest {

    @Test
    void declare_un_outil_de_recherche_a_un_seul_parametre() {
        assertThat(DocumentAgent.OUTILS).hasSize(1);
        ToolSpecification recherche = DocumentAgent.OUTILS.getFirst();

        assertThat(recherche.name()).isEqualTo(DocumentAgent.OUTIL_RECHERCHE);
        assertThat(recherche.parameters()).hasSize(1);
        assertThat(recherche.parameters().getFirst().name()).isEqualTo(DocumentAgent.PARAMETRE_QUESTION);
        assertThat(recherche.parameters().getFirst().required()).isTrue();
    }

    @Test
    void borne_la_boucle_par_quatre_tours_et_deux_minutes() {
        assertThat(DocumentAgent.BUDGET.maxTurns()).isEqualTo(4);
        assertThat(DocumentAgent.BUDGET.limit()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void garde_une_temperature_basse_contre_l_invention() {
        assertThat(DocumentAgent.TEMPERATURE).isLessThanOrEqualTo(0.3);
    }

    @Test
    void refuse_un_budget_sans_aucun_tour() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ExecutionBudget(0, Duration.ofSeconds(1)));
    }

    @Test
    void refuse_un_budget_de_temps_nul_ou_negatif() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ExecutionBudget(4, Duration.ZERO));
    }

    @Test
    void refuse_un_message_de_refus_vide() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AgentRefusals("  ", "hors périmètre"));
    }
}
