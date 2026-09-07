package xyz.sterenn.secondbrain.knowledge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

class CitationBufferTest {

    private static SourceCatalogue threeChunks() {
        UUID document = UUID.randomUUID();
        return SourceCatalogue.empty()
                .absorb(List.of(
                        new SourceCandidate(document, "a.pdf", 0, "", "un"),
                        new SourceCandidate(document, "a.pdf", 1, "", "deux"),
                        new SourceCandidate(document, "a.pdf", 2, "", "trois")))
                .catalogue();
    }

    @Test
    void lets_nothing_through_until_a_citation_has_appeared() {
        List<String> emitted = new ArrayList<>();
        CitationBuffer buffer = new CitationBuffer(threeChunks(), emitted::add);

        buffer.accept("Canberra est ");
        buffer.accept("la capitale de l'Australie.");

        assertThat(emitted).isEmpty();
        assertThat(buffer.isOpen()).isFalse();
        assertThat(buffer.text()).isEqualTo("Canberra est la capitale de l'Australie.");
    }

    @Test
    void opens_the_gates_at_the_first_citation_and_replays_what_came_before() {
        List<String> emitted = new ArrayList<>();
        CitationBuffer buffer = new CitationBuffer(threeChunks(), emitted::add);

        buffer.accept("Quatorze jours ");
        buffer.accept("[2].");
        buffer.accept(" Et ensuite [1].");

        assertThat(emitted).containsExactly("Quatorze jours [2].", " Et ensuite [1].");
        assertThat(buffer.isOpen()).isTrue();
    }

    @Test
    void recognises_a_citation_split_across_two_fragments() {
        List<String> emitted = new ArrayList<>();
        CitationBuffer buffer = new CitationBuffer(threeChunks(), emitted::add);

        buffer.accept("Quatorze jours [");
        assertThat(emitted).isEmpty();
        buffer.accept("3].");

        assertThat(emitted).containsExactly("Quatorze jours [3].");
    }

    @Test
    void does_not_open_on_a_citation_outside_the_catalogue() {
        List<String> emitted = new ArrayList<>();
        CitationBuffer buffer = new CitationBuffer(threeChunks(), emitted::add);

        buffer.accept("Canberra [9] est la capitale.");

        assertThat(emitted).isEmpty();
        assertThat(buffer.isOpen()).isFalse();
    }

    @Test
    void lets_the_sink_failure_propagate_at_opening() {
        CitationBuffer buffer = new CitationBuffer(threeChunks(), fragment -> {
            throw new IllegalStateException("le client a fermé");
        });

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> buffer.accept("Quatorze jours [1]."))
                .withMessageContaining("le client a fermé");
    }

    @Test
    void lets_the_sink_failure_propagate_once_the_stream_is_open() {
        List<String> emitted = new ArrayList<>();
        Consumer<String> sink = fragment -> {
            if (!emitted.isEmpty()) {
                throw new IllegalStateException("le client a fermé");
            }
            emitted.add(fragment);
        };
        CitationBuffer buffer = new CitationBuffer(threeChunks(), sink);
        buffer.accept("Quatorze jours [1].");
        assertThat(buffer.isOpen()).isTrue();

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> buffer.accept(" Et ensuite."))
                .withMessageContaining("le client a fermé");
    }
}
