package xyz.sterenn.secondbrain.knowledge.application.agent;

import java.util.function.Consumer;
import xyz.sterenn.secondbrain.knowledge.domain.CitationPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

/**
 * Holds fragments back until a known citation shows up: streaming as it comes and a guardrail at
 * the end of the answer are mutually exclusive, and grounding wins.
 */
final class CitationBuffer {

    private final SourceCatalogue catalogue;
    private final Consumer<String> output;
    private final StringBuilder accumulated = new StringBuilder();

    private boolean open;

    CitationBuffer(SourceCatalogue catalogue, Consumer<String> output) {
        this.catalogue = catalogue;
        this.output = output;
    }

    void accept(String fragment) {
        accumulated.append(fragment);
        if (open) {
            output.accept(fragment);
            return;
        }
        // On the accumulated text, never on the fragment: a [3] readily arrives split
        // into "[" then "3]".
        if (CitationPolicy.endOfFirstValidCitation(accumulated.toString(), catalogue::contains)
                .isPresent()) {
            open = true;
            output.accept(accumulated.toString());
        }
    }

    String text() {
        return accumulated.toString();
    }

    boolean isOpen() {
        return open;
    }
}
