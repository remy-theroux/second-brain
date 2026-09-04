package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.ArrayList;
import java.util.List;

public final class ExtractedTextBuilder {

    private final List<TextBlock> blocks = new ArrayList<>();

    public ExtractedTextBuilder section(String heading, int headingLevel, String text) {
        if (!TextBlock.normalise(text).isEmpty()) {
            blocks.add(TextBlock.of(heading, headingLevel, text));
        }
        return this;
    }

    public ExtractedTextBuilder untitled(String text) {
        return section("", 0, text);
    }

    public ExtractedText build() {
        return new ExtractedText(blocks);
    }
}
