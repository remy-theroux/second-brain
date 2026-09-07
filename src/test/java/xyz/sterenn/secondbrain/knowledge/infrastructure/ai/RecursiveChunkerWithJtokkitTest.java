package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.ChunkingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunker;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

class RecursiveChunkerWithJtokkitTest {

    private final TokenCounter tokenCounter = new JtokkitTokenCounter();
    private final RecursiveChunker chunker = new RecursiveChunker(tokenCounter);

    @Test
    void no_chunk_of_french_text_exceeds_the_ceiling() {
        String text = IntStream.range(0, 400)
                .mapToObj(index ->
                        "L'élève déchiffrait péniblement les hiéroglyphes gravés sur la stèle numéro " + index + ".")
                .collect(Collectors.joining(" "));

        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(text));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(tokenCounter.count(chunk.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void cuts_at_the_character_a_block_without_space_or_punctuation() {
        // The stub counter of RecursiveChunkerTest cannot reach this case: one 'word' is worth
        // exactly one token there.
        String blob = "QWxvcnNRdWVMZURvY3VtZW50TmVQb3J0ZUF1Y3VuZUZyb250aWVyZQ".repeat(400);

        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(blob));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(tokenCounter.count(chunk.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void the_character_cut_never_splits_a_surrogate_pair() {
        // An emoji is a UTF-16 surrogate pair: cut in the middle, the orphaned half is no
        // longer valid UTF-8, and the round-trip renders it as U+FFFD.
        String blob = "😀".repeat(2000);

        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(blob));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(tokenCounter.count(chunk.text())).isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS);
            assertThat(new String(chunk.text().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                    .isEqualTo(chunk.text());
        });
    }
}
