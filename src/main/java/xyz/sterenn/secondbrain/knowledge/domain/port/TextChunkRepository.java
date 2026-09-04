package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;

/**
 * Port sortant vers le stockage des extraits vectorisés : ils se lisent par l'identifiant de
 * leur document, dans l'ordre du document.
 */
public interface TextChunkRepository {

    List<TextChunk> saveAll(List<TextChunk> textChunks);

    List<TextChunk> findByDocumentId(UUID documentId);

    /**
     * AMQP livre au moins une fois et {@code (document_id, chunk_position)} est {@code UNIQUE} :
     * le handler efface avant d'écrire.
     */
    void deleteByDocumentId(UUID documentId);
}
