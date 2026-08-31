package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * L'adapter, contre un serveur bouché. Aucun appel ne sort de la machine : c'est la règle
 * du projet — « les deux adapters se testent avec des doublures, sans appel réseau ».
 *
 * <p>Pas de {@code @SpringBootTest} : l'adapter se construit à la main avec un
 * {@code RestClient.Builder} auquel {@link MockRestServiceServer} s'est branché. Démarrer un
 * contexte n'apprendrait rien de plus et coûterait quelques secondes à chaque exécution.
 */
class OllamaEmbeddingAdapterTest {

    private static final String BASE_URL = "http://ollama-de-test:11434";
    private static final String URL_EMBED = BASE_URL + "/api/embed";
    private static final String MODELE = "bge-m3";

    private MockRestServiceServer serveur;
    private OllamaEmbeddingAdapter adapter;

    @BeforeEach
    void brancher_le_serveur_bouche() {
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        adapter = new OllamaEmbeddingAdapter(builder, BASE_URL, MODELE);
    }

    @Test
    void rend_un_vecteur_par_texte_dans_le_meme_ordre() {
        serveur.expect(requestTo(URL_EMBED))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value(MODELE))
                .andExpect(jsonPath("$.input[0]").value("premier"))
                .andExpect(jsonPath("$.input[1]").value("second"))
                .andRespond(withSuccess(corpsDeReponse(0.1f, 0.2f), MediaType.APPLICATION_JSON));

        List<Embedding> vecteurs = adapter.embed(List.of("premier", "second"));

        assertThat(vecteurs).hasSize(2);
        assertThat(vecteurs.get(0).values()[0]).isEqualTo(0.1f);
        assertThat(vecteurs.get(1).values()[0]).isEqualTo(0.2f);
        serveur.verify();
    }

    @Test
    void decoupe_en_lots_et_recolle_les_resultats_dans_l_ordre() {
        // 120 textes, lots de 32 : 32 + 32 + 32 + 24, donc quatre appels.
        List<String> textes =
                IntStream.range(0, 120).mapToObj(i -> "texte " + i).toList();

        // Un lot après l'autre, chacun répondant des valeurs qui identifient son rang.
        // Quatre attentes successives : MockRestServiceServer les consomme dans l'ordre, et
        // `verify()` échoue s'il en reste une, donc « exactement quatre appels » est vérifié.
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(32, 0f));
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(32, 1f));
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(32, 2f));
        serveur.expect(requestTo(URL_EMBED)).andRespond(reponsePour(24, 3f));

        List<Embedding> vecteurs = adapter.embed(textes);

        assertThat(vecteurs).hasSize(120);
        assertThat(vecteurs.get(0).values()[0]).isEqualTo(0f);
        assertThat(vecteurs.get(31).values()[0]).isEqualTo(0f);
        assertThat(vecteurs.get(32).values()[0]).isEqualTo(1f);
        assertThat(vecteurs.get(119).values()[0]).isEqualTo(3f);
        serveur.verify();
    }

    @Test
    void n_appelle_pas_le_service_pour_une_liste_vide() {
        assertThat(adapter.embed(List.of())).isEmpty();

        serveur.verify(); // aucune attente posée : un appel ferait échouer la vérification
    }

    @Test
    void retente_trois_fois_puis_remonte_un_refus_affichable() {
        serveur.expect(ExpectedCount.times(OllamaEmbeddingAdapter.MAX_ATTEMPTS), requestTo(URL_EMBED))
                .andRespond(withServerError());

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("vectorisation");

        serveur.verify();
    }

    @Test
    void refuse_un_vecteur_dont_la_dimension_n_est_pas_celle_du_modele() {
        String corps = "{\"embeddings\":[[" + "0.5,".repeat(767) + "0.5]]}";
        serveur.expect(requestTo(URL_EMBED)).andRespond(withSuccess(corps, MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("768")
                .withMessageContaining(String.valueOf(EmbeddingPolicy.DIMENSIONS));
    }

    @Test
    void refuse_une_reponse_qui_ne_rend_pas_autant_de_vecteurs_que_de_textes() {
        serveur.expect(requestTo(URL_EMBED)).andRespond(withSuccess(corpsDeReponse(0.1f), MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("premier", "second")))
                .withMessageContaining("2");
    }

    /** Un corps JSON portant un vecteur de la bonne dimension par valeur donnée. */
    private static String corpsDeReponse(float... premieresValeurs) {
        StringBuilder corps = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < premieresValeurs.length; i++) {
            corps.append(i == 0 ? "" : ",").append(unVecteurJson(premieresValeurs[i]));
        }
        return corps.append("]}").toString();
    }

    /** Une réponse de {@code combien} vecteurs portant tous la même première valeur. */
    private static ResponseCreator reponsePour(int combien, float valeur) {
        StringBuilder corps = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < combien; i++) {
            corps.append(i == 0 ? "" : ",").append(unVecteurJson(valeur));
        }
        return withSuccess(corps.append("]}").toString(), MediaType.APPLICATION_JSON);
    }

    /** Un vecteur complet : la première valeur identifie le lot, le reste est du remplissage. */
    private static String unVecteurJson(float premiereValeur) {
        StringBuilder vecteur = new StringBuilder("[").append(premiereValeur);
        for (int i = 1; i < EmbeddingPolicy.DIMENSIONS; i++) {
            vecteur.append(",0.0");
        }
        return vecteur.append("]").toString();
    }
}
