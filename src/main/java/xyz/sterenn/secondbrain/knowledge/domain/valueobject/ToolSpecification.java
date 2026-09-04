package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record ToolSpecification(String name, String description, List<ToolParameter> parameters) {

    public ToolSpecification {
        Objects.requireNonNull(name, "Le nom de l'outil est obligatoire");
        Objects.requireNonNull(description, "La description de l'outil est obligatoire");
        parameters = List.copyOf(parameters);
    }
}
