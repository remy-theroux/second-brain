package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;

public record Absorption(SourceCatalogue catalogue, List<Source> newSources, int alreadySeen) {

    public Absorption {
        newSources = List.copyOf(newSources);
    }

    public boolean isEmpty() {
        return newSources.isEmpty() && alreadySeen == 0;
    }
}
