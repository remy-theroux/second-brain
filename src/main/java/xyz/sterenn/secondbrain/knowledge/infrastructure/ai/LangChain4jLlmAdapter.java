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
import tools.jackson.databind.ObjectMapper;
import xyz.sterenn.secondbrain.knowledge.domain.exception.LlmUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.LlmPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolCall;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

class LangChain4jLlmAdapter implements LlmPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StreamingChatModel chatModel;

    LangChain4jLlmAdapter(StreamingChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public LlmTurn stream(LlmRequest request, Consumer<String> onToken) {
        CompletableFuture<ChatResponse> attendu = new CompletableFuture<>();
        StringBuilder texte = new StringBuilder();

        chatModel.chat(versLangChain4j(request), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String fragment) {
                texte.append(fragment);
                // Une exception ici — le client a fermé sa connexion SSE — doit interrompre le
                // tour et remonter telle quelle : c'est le mécanisme d'annulation.
                try {
                    onToken.accept(fragment);
                } catch (RuntimeException abandon) {
                    // Emballée pour être reconnue à la sortie : c'est le client qui est parti,
                    // pas le service de génération qui a échoué.
                    attendu.completeExceptionally(new AbandonDuConsommateur(abandon));
                    throw abandon;
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse reponse) {
                attendu.complete(reponse);
            }

            @Override
            public void onError(Throwable echec) {
                attendu.completeExceptionally(echec);
            }
        });

        ChatResponse reponse = attendre(attendu);
        return new LlmTurn(texte.toString(), appelsDOutil(reponse.aiMessage()));
    }

    private static ChatResponse attendre(CompletableFuture<ChatResponse> attendu) {
        try {
            return attendu.join();
        } catch (CompletionException echec) {
            Throwable cause = echec.getCause() == null ? echec : echec.getCause();
            if (cause instanceof AbandonDuConsommateur abandon) {
                throw (RuntimeException) abandon.getCause();
            }
            throw new LlmUnavailableException(
                    "Le service de génération n'a pas répondu : " + cause.getMessage(), cause);
        }
    }

    /** Distingue « le client est parti » de « le service a échoué » sans deviner un type de la bibliothèque. */
    private static final class AbandonDuConsommateur extends RuntimeException {

        AbandonDuConsommateur(RuntimeException cause) {
            super(cause);
        }
    }

    private static List<ToolCall> appelsDOutil(AiMessage message) {
        if (!message.hasToolExecutionRequests()) {
            return List.of();
        }
        List<ToolCall> appels = new ArrayList<>();
        for (ToolExecutionRequest demande : message.toolExecutionRequests()) {
            String identifiant = demande.id() == null ? UUID.randomUUID().toString() : demande.id();
            appels.add(new ToolCall(identifiant, demande.name(), arguments(demande.arguments())));
        }
        return appels;
    }

    /** Les arguments arrivent en JSON ; le domaine ne connaît que des paires de chaînes. */
    private static Map<String, String> arguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, String> arguments = new LinkedHashMap<>();
        try {
            Map<?, ?> brut = OBJECT_MAPPER.readValue(json, Map.class);
            brut.forEach((cle, valeur) -> arguments.put(String.valueOf(cle), String.valueOf(valeur)));
        } catch (RuntimeException jsonIllisible) {
            // Un petit modèle produit des arguments illisibles : la boucle en fait une erreur
            // d'outil rendue au modèle, pas une panne.
            return Map.of();
        }
        return arguments;
    }

    private ChatRequest versLangChain4j(LlmRequest request) {
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
                                            .map(appel -> ToolExecutionRequest.builder()
                                                    .id(appel.id())
                                                    .name(appel.name())
                                                    .arguments(OBJECT_MAPPER.writeValueAsString(appel.arguments()))
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
                                .map(LangChain4jLlmAdapter::versLangChain4j)
                                .toList())
                        .build())
                .build();
    }

    private static dev.langchain4j.agent.tool.ToolSpecification versLangChain4j(ToolSpecification outil) {
        JsonObjectSchema.Builder schema = JsonObjectSchema.builder();
        outil.parameters().forEach(parametre -> {
            schema.addStringProperty(parametre.name(), parametre.description());
            if (parametre.required()) {
                schema.required(parametre.name());
            }
        });
        return dev.langchain4j.agent.tool.ToolSpecification.builder()
                .name(outil.name())
                .description(outil.description())
                .parameters(schema.build())
                .build();
    }
}
