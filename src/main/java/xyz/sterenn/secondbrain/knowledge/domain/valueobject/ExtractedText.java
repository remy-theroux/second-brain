package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

/** Le format commun à tous les documents extraits — voir ADR-0024. */
public record ExtractedText(List<TextBlock> blocks) {

    public ExtractedText {
        Objects.requireNonNull(blocks, "Les blocs de texte sont obligatoires");
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
        return blocks.stream().mapToInt(bloc -> bloc.getText().length()).sum();
    }
}
