package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OllamaChatConfiguration {

    /**
     * Generous, and chosen to detect a stuck service rather than to bound normal work: one
     * qwen3:4b turn on CPU, after ingesting eight chunks, takes tens of seconds. Same standing
     * as the RestClient {@code read-timeout} in application.yml.
     */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(180);

    @Bean
    LangChain4jLlmAdapter langChain4jLlmAdapter(
            @Value("${secondbrain.llm.base-url}") String baseUrl, @Value("${secondbrain.llm.model}") String model) {
        StreamingChatModel chatModel = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(READ_TIMEOUT)
                // Explicit: a `false` would leak the reasoning, <think> tags included, into the
                // answer text — checked, and it saves neither tokens nor latency.
                .think(true)
                .build();
        return new LangChain4jLlmAdapter(chatModel);
    }
}
