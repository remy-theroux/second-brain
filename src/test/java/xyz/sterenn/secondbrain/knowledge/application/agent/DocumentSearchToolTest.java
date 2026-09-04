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

    private final List<SearchChunks> demandes = new java.util.ArrayList<>();

    private QueryBus busQuiRend(List<ChunkMatchView> resultats) {
        return new QueryBus() {
            @Override
            @SuppressWarnings("unchecked")
            public <R> R ask(Query<R> query) {
                demandes.add((SearchChunks) query);
                return (R) resultats;
            }
        };
    }

    @Test
    void demande_la_recherche_pour_le_proprietaire_du_jeton() {
        UUID alice = UUID.randomUUID();
        DocumentSearchTool outil = new DocumentSearchTool(busQuiRend(List.of()));

        outil.rechercher("délai de rétractation", alice);

        assertThat(demandes).hasSize(1);
        assertThat(demandes.getFirst().question()).isEqualTo("délai de rétractation");
        assertThat(demandes.getFirst().ownerId()).isEqualTo(alice);
    }

    @Test
    void transforme_les_resultats_en_candidats_sans_leur_score() {
        UUID document = UUID.randomUUID();
        DocumentSearchTool outil = new DocumentSearchTool(
                busQuiRend(List.of(new ChunkMatchView(document, "rapport.pdf", 3, "Introduction", "texte", 0.87))));

        List<SourceCandidate> candidats = outil.rechercher("question", UUID.randomUUID());

        assertThat(candidats).containsExactly(new SourceCandidate(document, "rapport.pdf", 3, "Introduction", "texte"));
    }
}
