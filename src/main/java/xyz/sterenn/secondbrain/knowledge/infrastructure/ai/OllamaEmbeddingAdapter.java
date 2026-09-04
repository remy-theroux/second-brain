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
     * Assez grand pour amortir la latence d'un aller-retour, assez petit pour qu'un échec ne
     * coûte pas tout le document et que la mémoire d'un Ollama sur CPU tienne.
     */
    static final int BATCH_SIZE = 32;

    /**
     * 3 × 200 ms : de quoi couvrir un aléa réseau bref, pas un modèle qui charge à froid —
     * ce cas-là relève du délai de lecture du {@code RestClient} (voir {@code application.yml}).
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
        Objects.requireNonNull(texts, "La liste des textes à vectoriser est obligatoire");
        List<Embedding> vecteurs = new ArrayList<>(texts.size());
        for (int debut = 0; debut < texts.size(); debut += BATCH_SIZE) {
            vecteurs.addAll(embedBatch(texts.subList(debut, Math.min(debut + BATCH_SIZE, texts.size()))));
        }
        return List.copyOf(vecteurs);
    }

    /**
     * La {@link NullPointerException} est rattrapée avec l'{@link IllegalArgumentException} :
     * un corps du genre {@code {"embeddings":[null]}} ferait sinon remonter une exception
     * technique brute jusqu'au domaine.
     */
    private List<Embedding> embedBatch(List<String> lot) {
        OllamaEmbeddingResponse reponse = appelerAvecTentatives(lot);
        if (reponse == null
                || reponse.embeddings() == null
                || reponse.embeddings().size() != lot.size()) {
            int recus = reponse == null || reponse.embeddings() == null
                    ? 0
                    : reponse.embeddings().size();
            throw new EmbeddingUnavailableException("Le service de vectorisation a rendu " + recus + " vecteurs pour "
                    + lot.size() + " textes : sa réponse est inexploitable.");
        }
        try {
            return reponse.embeddings().stream().map(Embedding::of).toList();
        } catch (IllegalArgumentException | NullPointerException dimensionInattendue) {
            throw new EmbeddingUnavailableException(
                    "Le service de vectorisation ne produit pas des vecteurs de "
                            + EmbeddingPolicy.DIMENSIONS + " dimensions : " + dimensionInattendue.getMessage()
                            + ". Vérifier le modèle configuré.",
                    dimensionInattendue);
        }
    }

    /**
     * Un 4xx n'est pas retenté : sa réponse porte déjà la raison exacte, et retenter ne
     * changerait rien à la requête. Un 5xx ou une connexion qui échoue le sont.
     */
    private OllamaEmbeddingResponse appelerAvecTentatives(List<String> lot) {
        RestClientException dernierEchec = null;
        for (int tentative = 1; tentative <= MAX_ATTEMPTS; tentative++) {
            try {
                return restClient
                        .post()
                        .uri("/api/embed")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new OllamaEmbeddingRequest(model, lot))
                        .retrieve()
                        .body(OllamaEmbeddingResponse.class);
            } catch (HttpClientErrorException refusDuService) {
                throw new EmbeddingUnavailableException(
                        "Le service de vectorisation refuse la requête : " + refusDuService.getMessage(),
                        refusDuService);
            } catch (RestClientException echec) {
                dernierEchec = echec;
                if (tentative < MAX_ATTEMPTS) {
                    patienter();
                }
            }
        }
        throw new EmbeddingUnavailableException(
                "Le service de vectorisation est injoignable après " + MAX_ATTEMPTS + " tentatives.", dernierEchec);
    }

    private static void patienter() {
        try {
            Thread.sleep(RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException interruption) {
            // Réarmer le drapeau : l'appelant doit pouvoir constater l'interruption.
            Thread.currentThread().interrupt();
            throw new EmbeddingUnavailableException("La vectorisation a été interrompue.", interruption);
        }
    }
}
