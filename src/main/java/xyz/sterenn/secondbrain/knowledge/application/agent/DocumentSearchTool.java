package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.query.ChunkMatchView;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;

/**
 * Runs the tool the agent calls. The owner comes from the token and never from the model: an
 * agent able to name an owner would be a tenancy leak.
 */
@Component
class DocumentSearchTool {

    private final QueryBus queryBus;

    DocumentSearchTool(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    List<SourceCandidate> search(String question, UUID ownerId) {
        List<ChunkMatchView> results = queryBus.ask(new SearchChunks(question, ownerId));
        return results.stream()
                .map(view -> new SourceCandidate(
                        view.documentId(), view.filename(), view.position(), view.heading(), view.text()))
                .toList();
    }
}
