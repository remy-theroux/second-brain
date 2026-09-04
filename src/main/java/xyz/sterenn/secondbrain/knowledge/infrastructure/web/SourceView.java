package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Source;

record SourceView(int number, UUID documentId, String filename, int position, String heading, String text) {

    static SourceView of(Source source) {
        return new SourceView(
                source.number(),
                source.documentId(),
                source.filename(),
                source.position(),
                source.heading(),
                source.text());
    }
}
