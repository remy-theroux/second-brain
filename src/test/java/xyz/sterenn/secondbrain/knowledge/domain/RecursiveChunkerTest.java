package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class RecursiveChunkerTest {

    /** One word, one token. The stub that keeps the boundaries readable. */
    private static final TokenCounter ONE_WORD_ONE_TOKEN =
            text -> text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;

    private final RecursiveChunker chunker = new RecursiveChunker(ONE_WORD_ONE_TOKEN);

    @Test
    void rejects_a_missing_text() {
        assertThatNullPointerException().isThrownBy(() -> chunker.chunk(null));
    }

    @Test
    void a_document_shorter_than_a_chunk_yields_a_single_chunk() {
        ExtractedText text = ExtractedText.untitled(paragraph(1, 10));

        assertThat(chunker.chunk(text)).hasSize(1);
    }

    @Test
    void a_short_but_headed_document_yields_one_chunk_per_section() {
        ExtractedText text = new ExtractedText(List.of(
                TextBlock.of("Première section", 1, paragraph(1, 4)),
                TextBlock.of("Deuxième section", 1, paragraph(5, 4)),
                TextBlock.of("Troisième section", 1, paragraph(9, 4))));

        assertThat(chunker.chunk(text)).hasSize(3);
    }

    @Test
    void a_section_under_the_ceiling_yields_one_chunk_even_above_the_target() {
        ExtractedText text = ExtractedText.untitled(paragraph(1, 70));

        assertThat(chunker.chunk(text)).hasSize(1);
    }

    @Test
    void no_chunk_exceeds_the_ceiling() {
        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(paragraph(1, 200)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(ONE_WORD_ONE_TOKEN.count(chunk.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void no_chunk_starts_or_ends_in_the_middle_of_a_sentence() {
        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(paragraph(1, 200)));

        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.text()).endsWith(".");
            assertThat(chunk.text()).startsWith("Phrase numero ");
        });
    }

    @Test
    void two_consecutive_chunks_of_a_section_overlap() {
        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(paragraph(1, 200)));

        String firstSentenceOfTheSecond = chunks.get(1).text().split("(?<=\\.)\\s+")[0];
        assertThat(chunks.get(0).text()).contains(firstSentenceOfTheSecond);
    }

    @Test
    void the_overlap_does_not_cross_a_section_boundary() {
        ExtractedText text = new ExtractedText(List.of(
                TextBlock.of("Section A", 1, marker("Alpha", 200)), TextBlock.of("Section B", 1, marker("Beta", 200))));

        List<Chunk> chunks = chunker.chunk(text);

        assertThat(chunks)
                .filteredOn(chunk -> chunk.heading().equals("Section B"))
                .allSatisfy(chunk -> assertThat(chunk.text()).doesNotContain("Alpha"));
    }

    @Test
    void each_chunk_carries_the_heading_of_the_section_it_comes_from() {
        ExtractedText text = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, paragraph(1, 200)), TextBlock.of("Conclusion", 1, paragraph(1, 5))));

        List<Chunk> chunks = chunker.chunk(text);

        assertThat(chunks).extracting(Chunk::heading).contains("Introduction", "Conclusion");
        assertThat(chunks).last().satisfies(chunk -> assertThat(chunk.heading()).isEqualTo("Conclusion"));
    }

    @Test
    void splits_at_paragraphs_before_falling_back_to_sentences() {
        String body = paragraph(1, 30) + "\n\n" + paragraph(31, 30) + "\n\n" + paragraph(61, 30);

        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(body));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks.get(0).text()).contains("\n\n");
    }

    @Test
    void a_giant_paragraph_without_punctuation_is_cut_for_lack_of_a_boundary() {
        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled("mot ".repeat(3000)));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(ONE_WORD_ONE_TOKEN.count(chunk.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void the_overlap_yields_to_the_ceiling() {
        String giantSentence = "Mot " + "mot ".repeat(798) + "final.";

        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(paragraph(1, 60) + " " + giantSentence));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1).text()).isEqualTo(giantSentence.strip());
    }

    @Test
    void does_not_emit_a_chunk_that_the_next_one_contains_entirely() {
        ExtractedText text =
                ExtractedText.untitled("Introduction." + "\n\n" + paragraph(1, 70) + "\n\n" + paragraph(71, 70));

        List<Chunk> chunks = chunker.chunk(text);

        for (int index = 0; index < chunks.size() - 1; index++) {
            assertThat(chunks.get(index + 1).text())
                    .doesNotContain(chunks.get(index).text());
        }
    }

    @Test
    void loses_no_sentence_of_the_document() {
        List<Chunk> chunks = chunker.chunk(ExtractedText.untitled(paragraph(1, 200)));

        for (int number = 1; number <= 200; number++) {
            String expected = sentence(number);
            assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.text()).contains(expected));
        }
    }

    private static String sentence(int number) {
        return "Phrase numero " + number + " avec quelques mots pour occuper la place.";
    }

    private static String paragraph(int first, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> sentence(first + index))
                .collect(Collectors.joining(" "));
    }

    private static String marker(String marker, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> marker + " numero " + index + " avec quelques mots pour occuper la place.")
                .collect(Collectors.joining(" "));
    }
}
