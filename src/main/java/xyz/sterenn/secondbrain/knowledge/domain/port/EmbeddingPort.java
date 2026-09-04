package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Port sortant vers le service qui transforme du texte en vecteurs : autant de vecteurs que de
 * textes, et dans le même ordre.
 */
public interface EmbeddingPort {

    List<Embedding> embed(List<String> texts);
}
