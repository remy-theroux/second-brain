package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

@Component
class OllamaEmbeddingAdapter implements EmbeddingPort {

    /**
     * Large enough to amortise the latency of one round trip, small enough that a failure does
     * not cost the whole document and that an Ollama on CPU keeps its memory footprint.
     */
    static final int BATCH_SIZE = 32;

    /**
     * 3 × 200 ms: enough to cover a brief network hiccup, not a model loading cold — that case
     * belongs to the {@code RestClient} read timeout (see {@code application.yml}).
     */
    static final int MAX_ATTEMPTS = 3;

    private static final long RETRY_BACKOFF_MILLIS = 200L;

    private final RestClient restClient;
    private final String model;

    OllamaEmbeddingAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${secondbrain.embedding.base-url}") String baseUrl,
            @Value("${secondbrain.embedding.model}") String model) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.model = model;
    }

    @Override
    public List<Embedding> embed(List<String> texts) {
        Objects.requireNonNull(texts, "The list of texts to embed is required");
        List<Embedding> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            embeddings.addAll(embedBatch(texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()))));
        }
        return List.copyOf(embeddings);
    }

    /**
     * The {@link NullPointerException} is caught alongside the {@link IllegalArgumentException}:
     * a body such as {@code {"embeddings":[null]}} would otherwise let a raw technical exception
     * reach the domain.
     */
    private List<Embedding> embedBatch(List<String> batch) {
        OllamaEmbeddingResponse response = callWithRetries(batch);
        if (response == null
                || response.embeddings() == null
                || response.embeddings().size() != batch.size()) {
            int received = response == null || response.embeddings() == null
                    ? 0
                    : response.embeddings().size();
            throw new EmbeddingUnavailableException("Le service de vectorisation a rendu " + received
                    + " vecteurs pour " + batch.size() + " textes : sa réponse est inexploitable.");
        }
        try {
            return response.embeddings().stream().map(Embedding::of).toList();
        } catch (IllegalArgumentException | NullPointerException unexpectedDimension) {
            throw new EmbeddingUnavailableException(
                    "Le service de vectorisation ne produit pas des vecteurs de "
                            + EmbeddingPolicy.DIMENSIONS + " dimensions : " + unexpectedDimension.getMessage()
                            + ". Vérifier le modèle configuré.",
                    unexpectedDimension);
        }
    }

    /**
     * A 4xx is not retried: its response already carries the exact reason, and retrying would
     * not change the request. A 5xx or a failed connection are.
     */
    private OllamaEmbeddingResponse callWithRetries(List<String> batch) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return restClient
                        .post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new OllamaEmbeddingRequest(model, batch))
                        .retrieve()
                        .body(OllamaEmbeddingResponse.class);
            } catch (HttpClientErrorException serviceRefusal) {
                throw new EmbeddingUnavailableException(
                        "Le service de vectorisation refuse la requête : " + serviceRefusal.getMessage(),
                        serviceRefusal);
            } catch (RestClientException failure) {
                lastFailure = failure;
                if (attempt < MAX_ATTEMPTS) {
                    backOff();
                }
            }
        }
        throw new EmbeddingUnavailableException(
                "Le service de vectorisation est injoignable après " + MAX_ATTEMPTS + " tentatives.", lastFailure);
    }

    private static void backOff() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException interruption) {
            // Re-arm the flag: the caller must be able to observe the interruption.
            Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException("La vectorisation a été interrompue.", interruption);
        }
    }
}
