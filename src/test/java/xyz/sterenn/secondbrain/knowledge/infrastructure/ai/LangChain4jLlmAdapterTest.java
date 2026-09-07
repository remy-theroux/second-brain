package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.exception.LlmUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmMessage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmRequest;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.LlmTurn;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolParameter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ToolSpecification;

class LangChain4jLlmAdapterTest {

    private static final ToolSpecification TOOL = new ToolSpecification(
            "rechercher_dans_les_documents",
            "Recherche des extraits.",
            List.of(new ToolParameter("question", "La question à chercher.", true)));

    private HttpServer server;
    private LangChain4jLlmAdapter adapter;

    private void start(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        adapter = new OllamaChatConfiguration()
                .langChain4jLlmAdapter("http://127.0.0.1:" + server.getAddress().getPort(), "qwen3:4b");
    }

    @AfterEach
    void stop_the_server() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void returns_the_text_and_streams_it_as_it_comes() throws IOException {
        start(200, """
                {"message":{"role":"assistant","content":"Le délai "},"done":false}
                {"message":{"role":"assistant","content":"est de quatorze jours [2]."},"done":false}
                {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
                """);
        List<String> fragments = new ArrayList<>();

        LlmTurn turn = adapter.stream(
                new LlmRequest(List.of(LlmMessage.user("Quel délai ?")), List.of(TOOL), 0.2), fragments::add);

        assertThat(turn.requestsATool()).isFalse();
        assertThat(turn.text()).isEqualTo("Le délai est de quatorze jours [2].");
        assertThat(fragments).containsExactly("Le délai ", "est de quatorze jours [2].");
    }

    @Test
    void never_exposes_the_reasoning_received_on_its_own_channel() throws IOException {
        start(200, """
                {"message":{"role":"assistant","content":"","thinking":"L'utilisateur demande "},"done":false}
                {"message":{"role":"assistant","content":"","thinking":"le délai, je dois chercher."},"done":false}
                {"message":{"role":"assistant","content":"Le délai "},"done":false}
                {"message":{"role":"assistant","content":"est de quatorze jours."},"done":false}
                {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
                """);
        List<String> fragments = new ArrayList<>();

        LlmTurn turn = adapter.stream(
                new LlmRequest(List.of(LlmMessage.user("Quel délai ?")), List.of(), 0.2), fragments::add);

        assertThat(fragments).containsExactly("Le délai ", "est de quatorze jours.");
        assertThat(turn.text()).isEqualTo("Le délai est de quatorze jours.");
        assertThat(fragments).noneMatch(fragment -> fragment.contains("utilisateur") || fragment.contains("chercher"));
    }

    @Test
    void returns_a_tool_call_with_its_decoded_arguments() throws IOException {
        start(200, """
                {"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"rechercher_dans_les_documents","arguments":{"question":"délai de rétractation"}}}]},"done":true,"done_reason":"stop"}
                """);

        LlmTurn turn = adapter.stream(
                new LlmRequest(List.of(LlmMessage.user("Quel délai ?")), List.of(TOOL), 0.2), fragment -> {});

        assertThat(turn.requestsATool()).isTrue();
        assertThat(turn.toolCalls()).hasSize(1);
        assertThat(turn.toolCalls().getFirst().name()).isEqualTo("rechercher_dans_les_documents");
        assertThat(turn.toolCalls().getFirst().argument("question")).isEqualTo("délai de rétractation");
    }

    @Test
    void translates_a_service_failure_into_a_business_refusal() throws IOException {
        start(500, "{\"error\":\"model not found\"}");

        assertThatExceptionOfType(LlmUnavailableException.class)
                .isThrownBy(() -> adapter.stream(
                        new LlmRequest(List.of(LlmMessage.user("Bonjour")), List.of(), 0.2), fragment -> {}))
                .withMessageContaining("generation service");
    }

    @Test
    void lets_the_token_consumer_failure_bubble_up() throws IOException {
        start(200, """
                {"message":{"role":"assistant","content":"début"},"done":false}
                {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
                """);

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> adapter.stream(
                        new LlmRequest(List.of(LlmMessage.user("Bonjour")), List.of(), 0.2), fragment -> {
                            throw new IllegalStateException("le client a fermé");
                        }))
                .withMessageContaining("le client a fermé");
    }
}
