package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OllamaChatConfiguration {

    /**
     * Généreux et choisi pour détecter un service figé, non pour borner un traitement normal :
     * un tour de qwen3:4b sur CPU, après ingestion de huit extraits, se compte en dizaines de
     * secondes. Même statut que le {@code read-timeout} du RestClient dans application.yml.
     */
    private static final Duration DELAI_DE_LECTURE = Duration.ofSeconds(180);

    @Bean
    public LangChain4jLlmAdapter langChain4jLlmAdapter(
            @Value("${secondbrain.llm.base-url}") String baseUrl, @Value("${secondbrain.llm.model}") String model) {
        StreamingChatModel chatModel = OllamaStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(DELAI_DE_LECTURE)
                // qwen3 raisonne toujours avant de répondre ou d'appeler un outil : ce n'est pas
                // un bascule qui évite le raisonnement, seulement son canal. Explicite pour
                // qu'un `false` ultérieur, qui ferait fuir le raisonnement brut dans le texte de
                // réponse, ne passe pas pour une optimisation de latence.
                .think(true)
                .build();
        return new LangChain4jLlmAdapter(chatModel);
    }
}
