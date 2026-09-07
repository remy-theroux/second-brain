package xyz.sterenn.secondbrain.knowledge.domain.port;

/**
 * Outbound port to the yardstick that measures a text in tokens: it never throws, and returns
 * {@code 0} for a missing or empty text.
 */
public interface TokenCounter {

    int count(String text);
}
