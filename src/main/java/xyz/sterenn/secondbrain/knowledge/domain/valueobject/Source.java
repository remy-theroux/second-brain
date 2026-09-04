package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.UUID;

public record Source(int number, UUID documentId, String filename, int position, String heading, String text) {

    public Source {
        if (number < 1) {
            throw new IllegalArgumentException("Une source se numérote à partir de 1, pas " + number);
        }
    }

    static Source numerote(int number, SourceCandidate candidat) {
        return new Source(
                number,
                candidat.documentId(),
                candidat.filename(),
                candidat.position(),
                candidat.heading(),
                candidat.text());
    }
}
