package xyz.sterenn.secondbrain.knowledge.domain;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

public final class RecursiveChunker {

    private static final String PARAGRAPH_SEPARATOR = "\n\n";
    private static final String SENTENCE_SEPARATOR = " ";
    private static final String PARAGRAPH_BOUNDARY = "\n{2,}";
    private static final String WHITESPACE = "\\s+";

    private final TokenCounter tokenCounter;

    public RecursiveChunker(TokenCounter tokenCounter) {
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "Le compteur de tokens est obligatoire");
    }

    public List<Chunk> chunk(ExtractedText text) {
        Objects.requireNonNull(text, "Le texte extrait est obligatoire");
        List<Chunk> chunks = new ArrayList<>();
        for (TextBlock block : text.blocks()) {
            chunks.addAll(chunkSection(block.getHeading(), block.getText()));
        }
        return List.copyOf(chunks);
    }

    private List<Chunk> chunkSection(String heading, String body) {
        if (tokenCounter.count(body) <= ChunkingPolicy.MAX_TOKENS) {
            return List.of(new Chunk(heading, body));
        }
        List<Chunk> chunks = new ArrayList<>();
        String current = "";
        for (Unit unit : units(body)) {
            if (current.isEmpty()) {
                current = unit.text();
                continue;
            }
            String candidate = current + unit.separator() + unit.text();
            if (tokenCounter.count(candidate) <= ChunkingPolicy.TARGET_TOKENS) {
                current = candidate;
                continue;
            }
            List<String> sentences = sentences(current);
            List<String> kept = keptOverlap(sentences, unit);
            if (kept.size() < sentences.size()) {
                chunks.add(new Chunk(heading, current));
            }
            current = kept.isEmpty() ? unit.text() : join(kept) + unit.separator() + unit.text();
        }
        if (!current.isEmpty()) {
            chunks.add(new Chunk(heading, current));
        }
        return chunks;
    }

    private List<String> keptOverlap(List<String> sentences, Unit next) {
        List<String> kept = new ArrayList<>(trailingSentences(sentences));
        while (!kept.isEmpty()
                && tokenCounter.count(join(kept) + next.separator() + next.text()) > ChunkingPolicy.MAX_TOKENS) {
            kept.removeFirst();
        }
        return kept;
    }

    private List<String> trailingSentences(List<String> sentences) {
        List<String> kept = new ArrayList<>();
        int total = 0;
        for (int index = sentences.size() - 1; index >= 0; index--) {
            int cost = tokenCounter.count(sentences.get(index));
            if (total + cost > ChunkingPolicy.OVERLAP_TOKENS) {
                break;
            }
            kept.addFirst(sentences.get(index));
            total += cost;
        }
        return kept;
    }

    private List<Unit> units(String body) {
        List<Unit> units = new ArrayList<>();
        for (String paragraph : body.split(PARAGRAPH_BOUNDARY)) {
            String block = paragraph.strip();
            if (block.isEmpty()) {
                continue;
            }
            if (tokenCounter.count(block) <= ChunkingPolicy.MAX_TOKENS) {
                units.add(new Unit(block, PARAGRAPH_SEPARATOR));
                continue;
            }
            String separator = PARAGRAPH_SEPARATOR;
            for (String sentence : sentences(block)) {
                for (String piece : forceSplit(sentence)) {
                    units.add(new Unit(piece, separator));
                    separator = SENTENCE_SEPARATOR;
                }
            }
        }
        return units;
    }

    private List<String> forceSplit(String sentence) {
        if (tokenCounter.count(sentence) <= ChunkingPolicy.MAX_TOKENS) {
            return List.of(sentence);
        }
        List<String> pieces = new ArrayList<>();
        String current = "";
        for (String word : sentence.split(WHITESPACE)) {
            if (tokenCounter.count(word) > ChunkingPolicy.MAX_TOKENS) {
                if (!current.isEmpty()) {
                    pieces.add(current);
                    current = "";
                }
                pieces.addAll(splitOnCharacters(word));
                continue;
            }
            String candidate = current.isEmpty() ? word : current + SENTENCE_SEPARATOR + word;
            if (tokenCounter.count(candidate) > ChunkingPolicy.MAX_TOKENS) {
                pieces.add(current);
                current = word;
            } else {
                current = candidate;
            }
        }
        if (!current.isEmpty()) {
            pieces.add(current);
        }
        return pieces;
    }

    private List<String> splitOnCharacters(String word) {
        List<String> pieces = new ArrayList<>();
        // Token density is measured once: recounting it on every pass would make the split
        // quadratic.
        long tokens = Math.max(1, tokenCounter.count(word));
        // (long): the product overflows int past 2.7 million characters. The negative int that
        // came out of it brought the cut back to one character per pass.
        int estimate = (int) Math.max(1L, (long) word.length() * ChunkingPolicy.MAX_TOKENS / tokens);
        String remaining = word;
        while (!remaining.isEmpty()) {
            int size = Math.min(estimate, remaining.length());
            while (size > 1 && tokenCounter.count(remaining.substring(0, size)) > ChunkingPolicy.MAX_TOKENS) {
                size = size * 3 / 4;
            }
            // Never cut a surrogate pair in two: the orphaned half is no longer valid UTF-8,
            // and PostgreSQL refuses to write it.
            if (size > 1 && size < remaining.length() && Character.isHighSurrogate(remaining.charAt(size - 1))) {
                size--;
            }
            pieces.add(remaining.substring(0, size));
            remaining = remaining.substring(size);
        }
        return pieces;
    }

    /** A new instance on every call: {@code BreakIterator} is not safe under concurrent access. */
    private static List<String> sentences(String text) {
        BreakIterator boundaries = BreakIterator.getSentenceInstance(Locale.FRENCH);
        boundaries.setText(text);
        List<String> sentences = new ArrayList<>();
        int start = boundaries.first();
        for (int end = boundaries.next(); end != BreakIterator.DONE; start = end, end = boundaries.next()) {
            String sentence = text.substring(start, end).strip();
            if (!sentence.isEmpty()) {
                sentences.add(sentence);
            }
        }
        return sentences;
    }

    private static String join(List<String> sentences) {
        return String.join(SENTENCE_SEPARATOR, sentences);
    }

    private record Unit(String text, String separator) {}
}
