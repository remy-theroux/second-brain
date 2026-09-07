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
    void declares_a_search_tool_with_a_single_parameter() {
        assertThat(DocumentAgent.TOOLS).hasSize(1);
        ToolSpecification search = DocumentAgent.TOOLS.getFirst();

        assertThat(search.name()).isEqualTo(DocumentAgent.SEARCH_TOOL);
        assertThat(search.parameters()).hasSize(1);
        assertThat(search.parameters().getFirst().name()).isEqualTo(DocumentAgent.QUESTION_PARAMETER);
        assertThat(search.parameters().getFirst().required()).isTrue();
    }

    @Test
    void bounds_the_loop_by_four_turns_and_two_minutes() {
        assertThat(DocumentAgent.BUDGET.maxTurns()).isEqualTo(4);
        assertThat(DocumentAgent.BUDGET.limit()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void keeps_a_low_temperature_against_invention() {
        assertThat(DocumentAgent.TEMPERATURE).isLessThanOrEqualTo(0.3);
    }

    @Test
    void rejects_a_budget_without_any_turn() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ExecutionBudget(0, Duration.ofSeconds(1)));
    }

    @Test
    void rejects_a_zero_or_negative_time_budget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ExecutionBudget(4, Duration.ZERO));
    }

    @Test
    void rejects_a_blank_refusal_message() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new AgentRefusals("  ", "hors périmètre"));
    }
}
