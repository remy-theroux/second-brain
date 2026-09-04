package xyz.sterenn.secondbrain.knowledge.domain;

import java.time.Duration;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Agent;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.AgentRefusals;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExecutionBudget;

public final class AgentDeTest {

    public static final String AVEU = "Je ne trouve pas cette information dans vos documents.";
    public static final String HORS_PERIMETRE = "Je ne réponds qu'à partir de vos documents.";

    private AgentDeTest() {}

    public static Agent unAgent(String prose) {
        return new Agent(
                "agent-de-test",
                "v1",
                prose,
                new AgentRefusals(AVEU, HORS_PERIMETRE),
                DocumentAgent.OUTILS,
                new ExecutionBudget(4, Duration.ofSeconds(120)),
                0.2);
    }

    public static Agent unAgent() {
        return unAgent("Tu es un agent de test.");
    }
}
