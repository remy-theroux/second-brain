package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record LlmMessage(LlmMessage.Role role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL_RESULT
    }

    public LlmMessage {
        Objects.requireNonNull(role, "Le rôle du message est obligatoire");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(Role.SYSTEM, content, List.of(), null);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content, List.of(), null);
    }

    public static LlmMessage toolRequest(List<ToolCall> toolCalls) {
        return new LlmMessage(Role.ASSISTANT, "", toolCalls, null);
    }

    public static LlmMessage toolResult(String toolCallId, String content) {
        return new LlmMessage(Role.TOOL_RESULT, content, List.of(), toolCallId);
    }
}
