package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;

public record ToolParameter(String name, String description, boolean required) {

    public ToolParameter {
        Objects.requireNonNull(name, "A parameter name is required");
        Objects.requireNonNull(description, "A parameter description is required");
    }
}
