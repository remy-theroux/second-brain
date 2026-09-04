package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record ChunkMatch(UUID documentId, String filename, int position, Chunk chunk, double similarity) {

    public ChunkMatch {
        Objects.requireNonNull(documentId, "Le document dont cet extrait provient est obligatoire");
        Objects.requireNonNull(filename, "Le nom du document est obligatoire");
        Objects.requireNonNull(chunk, "L'extrait est obligatoire");
    }
}
