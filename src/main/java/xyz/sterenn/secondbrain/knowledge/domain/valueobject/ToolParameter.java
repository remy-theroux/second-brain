package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;

public record ToolParameter(String name, String description, boolean required) {

    public ToolParameter {
        Objects.requireNonNull(name, "Le nom du paramètre est obligatoire");
        Objects.requireNonNull(description, "La description du paramètre est obligatoire");
    }
}
