package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.UUID;

public record Source(int number, UUID documentId, String filename, int position, String heading, String text) {

    public Source {
        if (number < 1) {
            throw new IllegalArgumentException("A source is numbered from 1, not " + number);
        }
    }

    static Source numbered(int number, SourceCandidate candidate) {
        return new Source(
                number,
                candidate.documentId(),
                candidate.filename(),
                candidate.position(),
                candidate.heading(),
                candidate.text());
    }
}
