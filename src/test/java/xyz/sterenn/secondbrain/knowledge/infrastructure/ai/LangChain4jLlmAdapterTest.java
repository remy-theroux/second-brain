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

    private static final ToolSpecification OUTIL = new ToolSpecification(
            "rechercher_dans_les_documents",
            "Recherche des extraits.",
            List.of(new ToolParameter("question", "La question à chercher.", true)));

    private HttpServer serveur;
    private LangChain4jLlmAdapter adapter;

    private void demarrer(int statut, String corps) throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/api/chat", echange -> {
            echange.getRequestBody().readAllBytes();
            byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
            echange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            echange.sendResponseHeaders(statut, octets.length);
            echange.getResponseBody().write(octets);
            echange.close();
        });
        serveur.start();
        adapter = new OllamaChatConfiguration()
                .langChain4jLlmAdapter(
                        "http://127.0.0.1:" + serveur.getAddress().getPort(), "qwen3:4b");
    }

    @AfterEach
    void arreter_le_serveur() {
        if (serveur != null) {
            serveur.stop(0);
        }
    }

    @Test
    void rend_le_texte_et_le_remet_au_fil_de_l_eau() throws IOException {
        demarrer(200, """
                {"message":{"role":"assistant","content":"Le délai "},"done":false}
                {"message":{"role":"assistant","content":"est de quatorze jours [2]."},"done":false}
                {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
                """);
        List<String> fragments = new ArrayList<>();

        LlmTurn tour = adapter.stream(
                new LlmRequest(List.of(LlmMessage.user("Quel délai ?")), List.of(OUTIL), 0.2), fragments::add);

        assertThat(tour.requestsATool()).isFalse();
        assertThat(tour.text()).isEqualTo("Le délai est de quatorze jours [2].");
        assertThat(fragments).containsExactly("Le délai ", "est de quatorze jours [2].");
    }

    @Test
    void n_expose_jamais_le_raisonnement_recu_sur_son_propre_canal() throws IOException {
        demarrer(200, """
                {"message":{"role":"assistant","content":"","thinking":"L'utilisateur demande "},"done":false}
                {"message":{"role":"assistant","content":"","thinking":"le délai, je dois chercher."},"done":false}
                {"message":{"role":"assistant","content":"Le délai "},"done":false}
                {"message":{"role":"assistant","content":"est de quatorze jours."},"done":false}
                {"message":{"role":"assistant","content":""},"done":true,"done_reason":"stop"}
                """);
        List<String> fragments = new ArrayList<>();

        LlmTurn tour = adapter.stream(
                new LlmRequest(List.of(LlmMessage.user("Quel délai ?")), List.of(), 0.2), fragments::add);

        assertThat(fragments).containsExactly("Le délai ", "est de quatorze jours.");
        assertThat(tour.text()).isEqualTo("Le délai est de quatorze jours.");
        assertThat(fragments).noneMatch(fragment -> fragment.contains("utilisateur") || fragment.contains("chercher"));
    }

    @Test
    void rend_un_appel_d_outil_avec_ses_arguments_decodes() throws IOException {
        demarrer(200, """
                {"message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"rechercher_dans_les_documents","arguments":{"question":"délai de rétractation"}}}]},"done":true,"done_reason":"stop"}
                """);

        LlmTurn tour = adapter.stream(
                new LlmRequest(List.of(LlmMessage.user("Quel délai ?")), List.of(OUTIL), 0.2), fragment -> {});

        assertThat(tour.requestsATool()).isTrue();
        assertThat(tour.toolCalls()).hasSize(1);
        assertThat(tour.toolCalls().getFirst().name()).isEqualTo("rechercher_dans_les_documents");
        assertThat(tour.toolCalls().getFirst().argument("question")).isEqualTo("délai de rétractation");
    }

    @Test
    void traduit_une_panne_du_service_en_refus_metier() throws IOException {
        demarrer(500, "{\"error\":\"model not found\"}");

        assertThatExceptionOfType(LlmUnavailableException.class)
                .isThrownBy(() -> adapter.stream(
                        new LlmRequest(List.of(LlmMessage.user("Bonjour")), List.of(), 0.2), fragment -> {}))
                .withMessageContaining("génération");
    }

    @Test
    void laisse_remonter_l_echec_du_consommateur_de_tokens() throws IOException {
        demarrer(200, """
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
