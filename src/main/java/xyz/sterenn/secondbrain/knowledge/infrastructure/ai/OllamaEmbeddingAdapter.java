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

/**
 * Adapter Ollama du port {@link EmbeddingPort}. Un {@code POST /api/embed}, par lots, avec
 * trois tentatives.
 *
 * <p><strong>Écrit à la main plutôt que Spring AI</strong>, et c'est un écart assumé à RAG-2,
 * qui imposait ses starters. Trois raisons. On n'emploierait qu'une méthode de la
 * bibliothèque : son {@code VectorStore} imposerait son propre schéma, alors que le nôtre est
 * nommé par la typologie du document. Ses starters 2.0.0 tirent des dépendances alignées sur
 * Spring Boot 4.1 quand ce projet en épingle 4.0.7. Et le lotissement comme les tentatives
 * sont des règles à nous : déléguées, elles se liraient dans une propriété de configuration
 * au lieu du code qui les applique. RAG-9 reposera la question pour la génération, où la
 * bibliothèque mérite bien davantage son prix.
 *
 * <p><strong>Aucun contrôle au démarrage</strong>, second écart à RAG-2. Ce projet pratique
 * pourtant le fail-fast partout — table de routage des bus, couverture des extracteurs,
 * secret JWT sans défaut. La différence est de nature : ces trois-là sont des défauts de
 * <em>câblage</em>, déterministes et vrais une fois pour toutes, là où la disponibilité d'un
 * service d'inférence est une condition réseau qui change dans le temps. Un worker qui
 * refuserait de démarrer parce que le conteneur tire encore 2,2 Go de modèle n'aurait pas le
 * même sens qu'un worker mal câblé. Le prix est payé par le message
 * d'{@link EmbeddingUnavailableException}, qui nomme la vectorisation.
 *
 * <p>Package-private : rien au-dehors ne doit dépendre d'autre chose que du port.
 */
@Component
class OllamaEmbeddingAdapter implements EmbeddingPort {

    /**
     * Assez pour amortir la latence d'un aller-retour, assez peu pour qu'un échec ne coûte
     * pas tout le document et que la mémoire d'un Ollama sur CPU ne s'en émeuve pas. RAG-6
     * disait 100 ; le chiffre y était posé sans justification et triple le coût d'un échec.
     */
    static final int BATCH_SIZE = 32;

    /**
     * Couvre un aléa réseau bref, pas un modèle qui charge : 3 tentatives × 200 ms ne
     * totalisent que 400 ms de patience, loin des quelques secondes à quelques dizaines de
     * secondes que prend un chargement de modèle à froid. Ce cas-là est couvert par le
     * délai de lecture du {@code RestClient} (voir {@code application.yml}), pas ici.
     */
    static final int MAX_ATTEMPTS = 3;

    /** Court : on attend un modèle qui se charge, pas un service qui se répare. */
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
     * Un lot, et la traduction de tout ce qui peut mal tourner en un refus affichable.
     *
     * <p>C'est la règle des adapters de ce projet : aucune exception technique ne remonte à
     * l'application ni au domaine. L'{@link IllegalArgumentException} d'{@link Embedding} est
     * rattrapée ici pour la même raison qu'un adapter de persistance rattrape une violation
     * de contrainte — l'appelant n'a que faire de savoir <em>où</em> la dimension a été
     * vérifiée. La {@link NullPointerException} l'est tout autant : un corps du genre
     * {@code {"embeddings":[null]}} lèverait sinon une exception technique brute, exactement
     * ce que cette règle interdit.
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
     * Un aller-retour, avec jusqu'à {@link #MAX_ATTEMPTS} tentatives — sauf pour un refus
     * 4xx, qui n'en tente qu'une.
     *
     * <p>Un {@link HttpClientErrorException} est la réponse d'Ollama à une requête qu'il
     * juge mauvaise — modèle inconnu, corps malformé — et son message porte déjà la raison
     * exacte, tirée du corps JSON de la réponse. Retenter ne changerait rien à ce que dit la
     * requête, et coûterait trois fois le temps et les tentatives pour un problème que la
     * première réponse a déjà entièrement décrit. Une erreur 5xx ou une connexion qui échoue
     * restent, elles, retentées : rien ne dit qu'elles sont durables.
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
