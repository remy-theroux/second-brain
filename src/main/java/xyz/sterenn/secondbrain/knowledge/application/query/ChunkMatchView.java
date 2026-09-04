package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkMatch;

public record ChunkMatchView(
        UUID documentId, String filename, int position, String heading, String text, double similarity) {

    public static ChunkMatchView of(ChunkMatch chunkMatch) {
        return new ChunkMatchView(
                chunkMatch.documentId(),
                chunkMatch.filename(),
                chunkMatch.position(),
                chunkMatch.chunk().heading(),
                chunkMatch.chunk().text(),
                chunkMatch.similarity());
    }
}
