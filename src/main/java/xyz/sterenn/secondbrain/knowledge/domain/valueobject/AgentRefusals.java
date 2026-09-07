package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

public record AgentRefusals(String notFound, String outOfScope) {

    public AgentRefusals {
        if (notFound == null || notFound.isBlank()) {
            throw new IllegalArgumentException("L'aveu d'ignorance est obligatoire : c'est lui qu'on rend");
        }
        if (outOfScope == null || outOfScope.isBlank()) {
            throw new IllegalArgumentException("Le refus hors périmètre est obligatoire");
        }
        notFound = notFound.strip();
        outOfScope = outOfScope.strip();
    }
}
