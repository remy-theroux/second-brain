package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;

class EmbeddingTest {

    @Test
    void accepts_a_vector_of_the_expected_dimension() {
        Embedding vector = Embedding.of(aVector(EmbeddingPolicy.DIMENSIONS));

        assertThat(vector.values()).hasSize(EmbeddingPolicy.DIMENSIONS);
    }

    @Test
    void rejects_a_vector_too_short_while_naming_the_dimension_received() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Embedding.of(aVector(768)))
                .withMessageContaining("768")
                .withMessageContaining(String.valueOf(EmbeddingPolicy.DIMENSIONS));
    }

    @Test
    void rejects_a_missing_vector() {
        assertThatNullPointerException().isThrownBy(() -> Embedding.of(null));
    }

    @Test
    void two_vectors_with_the_same_content_are_equal() {
        assertThat(Embedding.of(aVector(EmbeddingPolicy.DIMENSIONS)))
                .isEqualTo(Embedding.of(aVector(EmbeddingPolicy.DIMENSIONS)))
                .hasSameHashCodeAs(Embedding.of(aVector(EmbeddingPolicy.DIMENSIONS)));
    }

    @Test
    void does_not_let_the_array_it_received_be_modified() {
        float[] source = aVector(EmbeddingPolicy.DIMENSIONS);
        Embedding vector = Embedding.of(source);

        source[0] = 42f;

        assertThat(vector.values()[0]).isEqualTo(0.5f);
    }

    @Test
    void does_not_let_the_array_it_returns_be_modified() {
        Embedding vector = Embedding.of(aVector(EmbeddingPolicy.DIMENSIONS));

        vector.values()[0] = 42f;

        assertThat(vector.values()[0]).isEqualTo(0.5f);
    }

    @Test
    void never_shows_its_values_when_printed() {
        assertThat(Embedding.of(aVector(EmbeddingPolicy.DIMENSIONS)).toString())
                .contains(String.valueOf(EmbeddingPolicy.DIMENSIONS))
                .doesNotContain("0.5");
    }

    private static float[] aVector(int dimensions) {
        float[] values = new float[dimensions];
        Arrays.fill(values, 0.5f);
        return values;
    }
}
