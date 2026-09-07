package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Outbound port to the service that turns text into vectors: as many vectors as texts, and in
 * the same order.
 */
public interface EmbeddingPort {

    List<Embedding> embed(List<String> texts);
}
