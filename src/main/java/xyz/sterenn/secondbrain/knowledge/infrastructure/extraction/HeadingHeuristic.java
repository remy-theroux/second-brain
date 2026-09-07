package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/** Guesses the headings of a PDF from font size, for want of an outline. See ADR-0027. */
final class HeadingHeuristic {

    private static final float HEADING_RATIO = 1.15f;

    private static final int MAX_HEADING_CHARACTERS = 120;

    private HeadingHeuristic() {}

    static List<Section> split(List<TextLine> lines) {
        if (lines.isEmpty()) {
            return List.of();
        }
        float bodySize = bodySize(lines);
        List<Float> headingSizes = headingSizes(lines, bodySize);

        List<Section> sections = new ArrayList<>();
        String heading = "";
        int level = 0;
        StringBuilder body = new StringBuilder();

        for (TextLine line : lines) {
            if (isHeading(line, bodySize)) {
                append(sections, heading, level, body);
                heading = line.text();
                level = Math.min(headingSizes.indexOf(rounded(line.fontSize())) + 1, TextBlock.MAX_HEADING_LEVEL);
                body.setLength(0);
            } else {
                // A line and not a paragraph: PDFTextStripper does not tell paragraph
                // boundaries apart (ADR-0027).
                body.append(line.text()).append('\n');
            }
        }
        append(sections, heading, level, body);
        return sections;
    }

    private static void append(List<Section> sections, String heading, int level, StringBuilder body) {
        if (!body.toString().isBlank()) {
            sections.add(new Section(heading, level, body.toString()));
        }
    }

    private static float bodySize(List<TextLine> lines) {
        Map<Float, Integer> charactersBySize = new HashMap<>();
        for (TextLine line : lines) {
            charactersBySize.merge(rounded(line.fontSize()), line.text().length(), Integer::sum);
        }
        return charactersBySize.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(0f);
    }

    private static List<Float> headingSizes(List<TextLine> lines, float bodySize) {
        return lines.stream()
                .filter(line -> isHeading(line, bodySize))
                .map(line -> rounded(line.fontSize()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private static boolean isHeading(TextLine line, float bodySize) {
        return !line.text().isBlank()
                && line.text().strip().length() <= MAX_HEADING_CHARACTERS
                && rounded(line.fontSize()) > bodySize * HEADING_RATIO;
    }

    /** To the half point: two glyphs of the same font sometimes differ by a few hundredths. */
    private static float rounded(float size) {
        return Math.round(size * 2f) / 2f;
    }
}
