package xyz.sterenn.secondbrain.knowledge.application.query;

import java.time.Instant;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

public record TextExtractionView(Instant extractedAt, int characterCount, List<TextBlockView> blocks) {

    public record TextBlockView(String heading, int headingLevel, String text) {}

    public static TextExtractionView of(TextExtraction extraction) {
        return new TextExtractionView(
                extraction.getExtractedAt(),
                extraction.text().characterCount(),
                extraction.getBlocks().stream().map(TextExtractionView::blockOf).toList());
    }

    private static TextBlockView blockOf(TextBlock block) {
        return new TextBlockView(block.getHeading(), block.getHeadingLevel(), block.getText());
    }
}
