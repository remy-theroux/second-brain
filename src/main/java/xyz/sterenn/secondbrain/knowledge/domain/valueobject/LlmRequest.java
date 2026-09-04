package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;

public record LlmRequest(List<LlmMessage> messages, List<ToolSpecification> tools, double temperature) {

    public LlmRequest {
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
    }
}
