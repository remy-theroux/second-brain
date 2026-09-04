package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.application.query.ChunkMatchView;
import xyz.sterenn.secondbrain.knowledge.application.query.SearchChunks;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;

/**
 * L'exécutant de l'outil que l'agent appelle. Le propriétaire vient du jeton et jamais du
 * modèle : un agent capable de nommer un propriétaire serait une faille de cloisonnement.
 */
@Component
class DocumentSearchTool {

    private final QueryBus queryBus;

    DocumentSearchTool(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    List<SourceCandidate> rechercher(String question, UUID ownerId) {
        List<ChunkMatchView> resultats = queryBus.ask(new SearchChunks(question, ownerId));
        return resultats.stream()
                .map(vue -> new SourceCandidate(
                        vue.documentId(), vue.filename(), vue.position(), vue.heading(), vue.text()))
                .toList();
    }
}
