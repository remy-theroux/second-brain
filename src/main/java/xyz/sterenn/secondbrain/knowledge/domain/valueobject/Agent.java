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
        Objects.requireNonNull(name, "Le nom de l'agent est obligatoire");
        Objects.requireNonNull(
                version,
                "La version de l'agent est obligatoire : sans elle, deux résultats"
                        + " produits par deux consignes différentes se comparent sans qu'on le sache");
        Objects.requireNonNull(refusals, "Les messages de refus sont obligatoires");
        Objects.requireNonNull(budget, "Le budget d'exécution est obligatoire");
        if (systemPrompt == null || systemPrompt.isBlank()) {
            throw new IllegalArgumentException("Un agent sans consignes n'en est pas un");
        }
        tools = List.copyOf(tools);
    }

    public boolean knows(String toolName) {
        return tools.stream().anyMatch(declared -> declared.name().equals(toolName));
    }
}
