package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;

public record Absorption(SourceCatalogue catalogue, List<Source> nouveaux, int dejaVus) {

    public Absorption {
        nouveaux = List.copyOf(nouveaux);
    }

    public boolean vide() {
        return nouveaux.isEmpty() && dejaVus == 0;
    }
}
