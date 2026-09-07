package xyz.sterenn.secondbrain.knowledge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.application.query.ChunkMatchView;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.shared.bus.Query;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;

class DocumentSearchToolTest {

    private final List<SearchChunks> requests = new java.util.ArrayList<>();

    private QueryBus busReturning(List<ChunkMatchView> results) {
        return new QueryBus() {
            @Override
            @SuppressWarnings("unchecked")
            public <R> R ask(Query<R> query) {
                requests.add((SearchChunks) query);
                return (R) results;
            }
        };
    }

    @Test
    void asks_the_search_for_the_token_owner() {
        UUID alice = UUID.randomUUID();
        DocumentSearchTool tool = new DocumentSearchTool(busReturning(List.of()));

        tool.search("délai de rétractation", alice);

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().question()).isEqualTo("délai de rétractation");
        assertThat(requests.getFirst().ownerId()).isEqualTo(alice);
    }

    @Test
    void turns_the_results_into_candidates_without_their_score() {
        UUID document = UUID.randomUUID();
        DocumentSearchTool tool = new DocumentSearchTool(
                busReturning(List.of(new ChunkMatchView(document, "rapport.pdf", 3, "Introduction", "texte", 0.87))));

        List<SourceCandidate> candidates = tool.search("question", UUID.randomUUID());

        assertThat(candidates)
                .containsExactly(new SourceCandidate(document, "rapport.pdf", 3, "Introduction", "texte"));
    }
}
