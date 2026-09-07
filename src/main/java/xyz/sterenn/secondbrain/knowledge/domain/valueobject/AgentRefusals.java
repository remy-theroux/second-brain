package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

public record AgentRefusals(String notFound, String outOfScope) {

    public AgentRefusals {
        if (notFound == null || notFound.isBlank()) {
            throw new IllegalArgumentException("The admission of ignorance is required: it is what gets returned");
        }
        if (outOfScope == null || outOfScope.isBlank()) {
            throw new IllegalArgumentException("The out-of-scope refusal is required");
        }
        notFound = notFound.strip();
        outOfScope = outOfScope.strip();
    }
}
