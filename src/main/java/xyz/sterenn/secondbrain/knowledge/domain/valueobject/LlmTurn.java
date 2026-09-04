package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;

public record LlmTurn(String text, List<ToolCall> toolCalls) {

    public LlmTurn {
        text = text == null ? "" : text;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public boolean demandeUnOutil() {
        return !toolCalls.isEmpty();
    }
}
