package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import xyz.sterenn.secondbrain.knowledge.domain.exception.LlmUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

class LangChain4jLlmAdapter implements LlmPort {

    private static final Logger LOG = LoggerFactory.getLogger(LangChain4jLlmAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamingChatModel chatModel;

    LangChain4jLlmAdapter(StreamingChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmTurn stream(LlmRequest request, Consumer<String> onToken) {
        CompletableFuture<ChatResponse> pending = new CompletableFuture<>();
        StringBuilder text = new StringBuilder();

        chatModel.chat(toLangChain4j(request), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String fragment) {
                text.append(fragment);
                // An exception here — the client closed its SSE connection — must break the turn
                // and propagate as is: that is the cancellation mechanism.
                try {
                    onToken.accept(fragment);
                } catch (RuntimeException abort) {
                    // Wrapped so it can be recognised on the way out: the client left, the
                    // generation service did not fail.
                    pending.completeExceptionally(new ConsumerFailure(abort));
                    throw abort;
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                pending.complete(response);
            }

            @Override
            public void onError(Throwable failure) {
                pending.completeExceptionally(failure);
            }
        });

        ChatResponse response = await(pending);
        return new LlmTurn(text.toString(), toolCallsOf(response.aiMessage()));
    }

    private static ChatResponse await(CompletableFuture<ChatResponse> pending) {
        try {
            return pending.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof ConsumerFailure abort) {
                throw (RuntimeException) abort.getCause();
            }
            throw new LlmUnavailableException("The generation service did not respond: " + cause.getMessage(), cause);
        }
    }

    /** Tells "the client left" from "the service failed" without guessing a library type. */
    private static final class ConsumerFailure extends RuntimeException {

        ConsumerFailure(RuntimeException cause) {
            super(cause);
        }
    }

    private static List<ToolCall> toolCallsOf(AiMessage message) {
        if (!message.hasToolExecutionRequests()) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (ToolExecutionRequest request : message.toolExecutionRequests()) {
            String id = request.id() == null ? UUID.randomUUID().toString() : request.id();
            calls.add(new ToolCall(id, request.name(), arguments(request.name(), request.arguments())));
        }
        return calls;
    }

    /** Arguments arrive as JSON; the domain only knows pairs of strings. */
    private static Map<String, String> arguments(String toolName, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        try {
            Map<?, ?> raw = OBJECT_MAPPER.readValue(json, Map.class);
            raw.forEach((key, value) -> arguments.put(String.valueOf(key), String.valueOf(value)));
        } catch (RuntimeException unreadableJson) {
            // A small model produces unreadable arguments: the loop turns that into a tool error
            // handed back to the model, not an outage — but the operator must be able to see it.
            LOG.warn("Unreadable arguments for tool '{}': {}", toolName, unreadableJson.getMessage());
            return Map.of();
        }
        return arguments;
    }

    private ChatRequest toLangChain4j(LlmRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        for (LlmMessage message : request.messages()) {
            messages.add(
                    switch (message.role()) {
                        case SYSTEM -> SystemMessage.from(message.content());
                        case USER -> UserMessage.from(message.content());
                        case ASSISTANT ->
                            message.toolCalls().isEmpty()
                                    ? AiMessage.from(message.content())
                                    : AiMessage.from(message.toolCalls().stream()
                                            .map(call -> ToolExecutionRequest.builder()
                                                    .id(call.id())
                                                    .name(call.name())
                                                    .arguments(OBJECT_MAPPER.writeValueAsString(call.arguments()))
                                                    .build())
                                            .toList());
                        case TOOL_RESULT ->
                            ToolExecutionResultMessage.from(message.toolCallId(), null, message.content());
                    });
        }
        return ChatRequest.builder()
                .messages(messages)
                .parameters(ChatRequestParameters.builder()
                        .temperature(request.temperature())
                        .toolSpecifications(request.tools().stream()
                                .map(LangChain4jLlmAdapter::toLangChain4j)
                                .toList())
                        .build())
                .build();
    }

    private static dev.langchain4j.agent.tool.ToolSpecification toLangChain4j(ToolSpecification tool) {
        JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
        tool.parameters().forEach(parameter -> {
            schema.addStringProperty(parameter.name(), parameter.description());
            if (parameter.required()) {
                schema.required(parameter.name());
            }
        });
        return dev.langchain4j.agent.tool.ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .parameters(schema.build())
                .build();
    }
}
