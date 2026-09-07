package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkMatch;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Outbound port to the storage of vectorised chunks: they are read by their document
 * identifier, in document order, or by proximity to a vector.
 */
public interface TextChunkRepository {

    List<TextChunk> saveAll(List<TextChunk> textChunks);

    List<TextChunk> findByDocumentId(UUID documentId);

    /**
     * AMQP delivers at least once and {@code (document_id, chunk_position)} is {@code UNIQUE}:
     * the handler deletes before writing.
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * The owner's chunks closest to the given vector, from nearest to farthest, at most {@code
     * limit}.
     */
    List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit);
}
