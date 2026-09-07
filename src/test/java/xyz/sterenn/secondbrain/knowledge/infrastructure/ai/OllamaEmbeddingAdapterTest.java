package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseCreator;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.EmbeddingUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

class OllamaEmbeddingAdapterTest {

    private static final String BASE_URL = "http://ollama-de-test:11434";
    private static final String URL_EMBED = BASE_URL + "/api/embed";
    private static final String MODEL = "bge-m3";

    private MockRestServiceServer server;
    private OllamaEmbeddingAdapter adapter;

    @BeforeEach
    void wire_the_stub_server() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new OllamaEmbeddingAdapter(builder, BASE_URL, MODEL);
    }

    @Test
    void returns_one_vector_per_text_in_the_same_order() {
        server.expect(requestTo(URL_EMBED))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.input[0]").value("premier"))
                .andExpect(jsonPath("$.input[1]").value("second"))
                .andRespond(withSuccess(responseBody(0.1f, 0.2f), MediaType.APPLICATION_JSON));

        List<Embedding> vectors = adapter.embed(List.of("premier", "second"));

        assertThat(vectors).hasSize(2);
        assertThat(vectors.get(0).values()[0]).isEqualTo(0.1f);
        assertThat(vectors.get(1).values()[0]).isEqualTo(0.2f);
        server.verify();
    }

    @Test
    void splits_into_batches_and_reassembles_the_results_in_order() {
        // 120 texts, batches of 32: 32 + 32 + 32 + 24, hence four calls.
        List<String> texts = IntStream.range(0, 120).mapToObj(i -> "texte " + i).toList();

        // MockRestServiceServer consumes the expectations in order and `verify()` fails if one
        // is left: four expectations therefore mean exactly four calls.
        server.expect(requestTo(URL_EMBED)).andRespond(responseOf(32, 0f));
        server.expect(requestTo(URL_EMBED))
                .andExpect(jsonPath("$.input[0]").value("texte 32"))
                .andExpect(jsonPath("$.input[31]").value("texte 63"))
                .andRespond(responseOf(32, 1f));
        server.expect(requestTo(URL_EMBED)).andRespond(responseOf(32, 2f));
        server.expect(requestTo(URL_EMBED))
                .andExpect(jsonPath("$.input[0]").value("texte 96"))
                .andExpect(jsonPath("$.input.length()").value(24))
                .andRespond(responseOf(24, 3f));

        List<Embedding> vectors = adapter.embed(texts);

        assertThat(vectors).hasSize(120);
        assertThat(vectors.get(0).values()[0]).isEqualTo(0f);
        assertThat(vectors.get(31).values()[0]).isEqualTo(0f);
        assertThat(vectors.get(32).values()[0]).isEqualTo(1f);
        assertThat(vectors.get(119).values()[0]).isEqualTo(3f);
        server.verify();
    }

    @Test
    void does_not_call_the_service_for_an_empty_list() {
        assertThat(adapter.embed(List.of())).isEmpty();

        server.verify(); // no expectation set: a call would fail the verification
    }

    @Test
    void retries_three_times_then_surfaces_a_displayable_refusal() {
        server.expect(ExpectedCount.times(OllamaEmbeddingAdapter.MAX_ATTEMPTS), requestTo(URL_EMBED))
                .andRespond(withServerError());

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("vectorisation");

        server.verify();
    }

    @Test
    void rejects_a_vector_whose_dimension_is_not_the_model_one() {
        String body = "{\"embeddings\":[[" + "0.5,".repeat(767) + "0.5]]}";
        server.expect(requestTo(URL_EMBED)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("768")
                .withMessageContaining(String.valueOf(EmbeddingPolicy.DIMENSIONS));
        server.verify();
    }

    @Test
    void rejects_a_response_that_returns_fewer_vectors_than_texts() {
        server.expect(requestTo(URL_EMBED)).andRespond(withSuccess(responseBody(0.1f), MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("premier", "second")))
                .withMessageContaining("1 vecteurs pour 2 textes");
        server.verify();
    }

    @Test
    void does_not_insist_on_a_4xx_refusal_and_keeps_its_message() {
        String errorBody = "{\"error\":\"model \\\"bge-m4\\\" not found, try pulling it first\"}";
        server.expect(ExpectedCount.once(), requestTo(URL_EMBED))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody));

        assertThatExceptionOfType(EmbeddingUnavailableException.class)
                .isThrownBy(() -> adapter.embed(List.of("un texte")))
                .withMessageContaining("bge-m4")
                .withMessageContaining("not found");
        server.verify();
    }

    private static String responseBody(float... firstValues) {
        StringBuilder body = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < firstValues.length; i++) {
            body.append(i == 0 ? "" : ",").append(oneVectorJson(firstValues[i]));
        }
        return body.append("]}").toString();
    }

    private static ResponseCreator responseOf(int howMany, float value) {
        StringBuilder body = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < howMany; i++) {
            body.append(i == 0 ? "" : ",").append(oneVectorJson(value));
        }
        return withSuccess(body.append("]}").toString(), MediaType.APPLICATION_JSON);
    }

    /** A full vector: the first value identifies the batch, the rest is padding. */
    private static String oneVectorJson(float firstValue) {
        StringBuilder vector = new StringBuilder("[").append(firstValue);
        for (int i = 1; i < EmbeddingPolicy.DIMENSIONS; i++) {
            vector.append(",0.0");
        }
        return vector.append("]").toString();
    }
}
