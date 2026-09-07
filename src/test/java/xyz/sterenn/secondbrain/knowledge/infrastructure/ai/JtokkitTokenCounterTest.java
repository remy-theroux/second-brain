package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

class JtokkitTokenCounterTest {

    private final TokenCounter tokenCounter = new JtokkitTokenCounter();

    @Test
    void counts_nothing_in_a_missing_or_empty_text() {
        assertThat(tokenCounter.count(null)).isZero();
        assertThat(tokenCounter.count("")).isZero();
    }

    @Test
    void counts_at_least_one_token_per_word() {
        assertThat(tokenCounter.count("Bonjour")).isPositive();
    }

    @Test
    void counts_more_tokens_than_words_on_accented_french() {
        String text = "L'élève déchiffrait péniblement les hiéroglyphes gravés sur la stèle funéraire. ".repeat(20);
        int words = text.strip().split("\\s+").length;

        assertThat(tokenCounter.count(text)).isGreaterThan(words);
    }

    @Test
    void counts_more_for_a_longer_text() {
        String sentence = "Le chat dort sur le tapis.";

        assertThat(tokenCounter.count(sentence.repeat(3))).isGreaterThan(tokenCounter.count(sentence));
    }
}
