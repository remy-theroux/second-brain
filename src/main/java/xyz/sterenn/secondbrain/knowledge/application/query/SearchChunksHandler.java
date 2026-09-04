package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.SearchPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.port.EmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Question;
import xyz.sterenn.secondbrain.shared.bus.QueryHandler;

@Component
public class SearchChunksHandler implements QueryHandler<SearchChunks, List<ChunkMatchView>> {

    private final EmbeddingPort embeddingPort;
    private final TextChunkRepository textChunkRepository;

    public SearchChunksHandler(EmbeddingPort embeddingPort, TextChunkRepository textChunkRepository) {
        this.embeddingPort = embeddingPort;
        this.textChunkRepository = textChunkRepository;
    }

    @Override
    public List<ChunkMatchView> handle(SearchChunks query) {
        Question question = new Question(query.question());
        Embedding vecteur = embeddingPort.embed(List.of(question.value())).getFirst();
        return textChunkRepository.findNearest(query.ownerId(), vecteur, SearchPolicy.RESULTS).stream()
                .map(ChunkMatchView::of)
                .toList();
    }
}
