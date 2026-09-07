package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Map;
import java.util.Objects;

public record ToolCall(String id, String name, Map<String, String> arguments) {

    public ToolCall {
        Objects.requireNonNull(id, "L'identifiant de l'appel d'outil est obligatoire");
        Objects.requireNonNull(name, "Le nom de l'outil appelé est obligatoire");
        arguments = Map.copyOf(arguments);
    }

    public String argument(String name) {
        return arguments.get(name);
    }
}
