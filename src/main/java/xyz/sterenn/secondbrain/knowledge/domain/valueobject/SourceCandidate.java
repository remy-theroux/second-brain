package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record SourceCandidate(UUID documentId, String filename, int position, String heading, String text) {

    public SourceCandidate {
        Objects.requireNonNull(documentId, "The document this chunk comes from is required");
        Objects.requireNonNull(filename, "The document filename is required");
        Objects.requireNonNull(heading, "A section heading is required, empty when there is none");
        Objects.requireNonNull(text, "The chunk text is required");
    }
}
