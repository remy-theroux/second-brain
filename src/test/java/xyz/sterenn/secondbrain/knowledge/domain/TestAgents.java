package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AgentRefusals;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExecutionBudget;

public final class TestAgents {

    public static final String NOT_FOUND = "Je ne trouve pas cette information dans vos documents.";
    public static final String OUT_OF_SCOPE = "Je ne réponds qu'à partir de vos documents.";

    private TestAgents() {}

    public static Agent anAgent(String prose) {
        return new Agent(
                "agent-de-test",
                "v1",
                prose,
                new AgentRefusals(NOT_FOUND, OUT_OF_SCOPE),
                DocumentAgent.TOOLS,
                new ExecutionBudget(4, Duration.ofSeconds(120)),
                0.2);
    }

    public static Agent anAgent() {
        return anAgent("Tu es un agent de test.");
    }
}
