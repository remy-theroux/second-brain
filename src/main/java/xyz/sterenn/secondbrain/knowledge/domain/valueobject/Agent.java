package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record Agent(
        String name,
        String version,
        String systemPrompt,
        AgentRefusals refusals,
        List<ToolSpecification> tools,
        ExecutionBudget budget,
        double temperature) {

    public Agent {
        Objects.requireNonNull(name, "An agent name is required");
        Objects.requireNonNull(
                version,
                "An agent version is required: without it, two results"
                        + " produced by two different sets of instructions are compared without anyone knowing");
        Objects.requireNonNull(refusals, "Refusal messages are required");
        Objects.requireNonNull(budget, "An execution budget is required");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("An agent without instructions is not an agent");
        }
        tools = List.copyOf(tools);
    }

    public boolean knows(String toolName) {
        return tools.stream().anyMatch(declared -> declared.name().equals(toolName));
    }
}
