package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Map;
import java.util.Objects;

public record ToolCall(String id, String name, Map<String, String> arguments) {

    public ToolCall {
        Objects.requireNonNull(id, "A tool call id is required");
        Objects.requireNonNull(name, "The name of the called tool is required");
        arguments = Map.copyOf(arguments);
    }

    public String argument(String name) {
        return arguments.get(name);
    }
}
