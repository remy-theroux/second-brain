package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedTextBuilder;

/**
 * Les sections brutes sortent du {@code try} des extracteurs : construire un type du domaine
 * dedans ferait passer {@link UnextractableDocumentException}, un refus métier, pour une
 * panne de lecture traduite en {@link UnreadableDocumentException}.
 */
record Section(String heading, int level, String body) {

    static Section untitled(String body) {
        return new Section("", 0, body);
    }

    static ExtractedText assemble(List<Section> sections) {
        ExtractedTextBuilder blocs = new ExtractedTextBuilder();
        sections.forEach(section -> blocs.section(section.heading(), section.level(), section.body()));
        return blocs.build();
    }
}
