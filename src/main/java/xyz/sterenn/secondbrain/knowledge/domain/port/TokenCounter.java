package xyz.sterenn.secondbrain.knowledge.domain.port;

/**
 * Port sortant vers la toise qui mesure un texte en tokens : elle ne lève jamais, et rend
 * {@code 0} pour un texte absent ou vide.
 */
public interface TokenCounter {

    int count(String text);
}
