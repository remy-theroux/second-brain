package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record ChunkMatch(UUID documentId, String filename, int position, Chunk chunk, double similarity) {

    public ChunkMatch {
        Objects.requireNonNull(documentId, "The document this chunk comes from is required");
        Objects.requireNonNull(filename, "The document filename is required");
        Objects.requireNonNull(chunk, "A chunk is required");
    }
}
