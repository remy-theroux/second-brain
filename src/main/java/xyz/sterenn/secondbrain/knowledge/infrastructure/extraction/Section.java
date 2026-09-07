package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedTextBuilder;

/**
 * Raw sections come out of the extractors' {@code try}: building a domain type inside it would
 * make {@link UnextractableDocumentException}, a business refusal, look like a read failure
 * translated into {@link UnreadableDocumentException}.
 */
record Section(String heading, int level, String body) {

    static Section untitled(String body) {
        return new Section("", 0, body);
    }

    static ExtractedText assemble(List<Section> sections) {
        ExtractedTextBuilder blocks = new ExtractedTextBuilder();
        sections.forEach(section -> blocks.section(section.heading(), section.level(), section.body()));
        return blocks.build();
    }
}
