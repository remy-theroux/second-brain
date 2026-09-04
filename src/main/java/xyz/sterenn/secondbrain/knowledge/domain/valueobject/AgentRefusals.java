package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

public record AgentRefusals(String introuvable, String horsPerimetre) {

    public AgentRefusals {
        if (introuvable == null || introuvable.isBlank()) {
            throw new IllegalArgumentException("L'aveu d'ignorance est obligatoire : c'est lui qu'on rend");
        }
        if (horsPerimetre == null || horsPerimetre.isBlank()) {
            throw new IllegalArgumentException("Le refus hors périmètre est obligatoire");
        }
        introuvable = introuvable.strip();
        horsPerimetre = horsPerimetre.strip();
    }
}
