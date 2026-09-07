package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

/** The format shared by every extracted document — see ADR-0024. */
public record ExtractedText(List<TextBlock> blocks) {

    public ExtractedText {
        Objects.requireNonNull(blocks, "Text blocks are required");
        blocks = List.copyOf(blocks);
        if (!ExtractionPolicy.isExploitable(characterCount(blocks))) {
            throw new UnextractableDocumentException();
        }
    }

    public static ExtractedText untitled(String text) {
        return new ExtractedText(List.of(TextBlock.untitled(text)));
    }

    public int characterCount() {
        return characterCount(blocks);
    }

    private static int characterCount(List<TextBlock> blocks) {
        return blocks.stream().mapToInt(block -> block.getText().length()).sum();
    }
}
