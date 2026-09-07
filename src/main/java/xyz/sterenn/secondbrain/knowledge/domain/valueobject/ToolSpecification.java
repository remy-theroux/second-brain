package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record ToolSpecification(String name, String description, List<ToolParameter> parameters) {

    public ToolSpecification {
        Objects.requireNonNull(name, "A tool name is required");
        Objects.requireNonNull(description, "A tool description is required");
        parameters = List.copyOf(parameters);
    }
}
