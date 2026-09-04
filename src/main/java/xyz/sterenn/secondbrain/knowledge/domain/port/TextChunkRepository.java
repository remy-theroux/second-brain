package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkMatch;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

/**
 * Port sortant vers le stockage des extraits vectorisés : ils se lisent par l'identifiant de
 * leur document, dans l'ordre du document, ou par proximité avec un vecteur.
 */
public interface TextChunkRepository {

    List<TextChunk> saveAll(List<TextChunk> textChunks);

    List<TextChunk> findByDocumentId(UUID documentId);

    /**
     * AMQP livre au moins une fois et {@code (document_id, chunk_position)} est {@code UNIQUE} :
     * le handler efface avant d'écrire.
     */
    void deleteByDocumentId(UUID documentId);

    /**
     * Les extraits du propriétaire les plus proches du vecteur donné, du plus proche au plus
     * lointain, au plus {@code limit}.
     */
    List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit);
}
