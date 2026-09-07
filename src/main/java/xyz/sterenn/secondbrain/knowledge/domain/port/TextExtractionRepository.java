package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;

/**
 * Outbound port to the storage of extracted text: it is read by its document identifier, the
 * partitioning by owner having already been done upstream.
 */
public interface TextExtractionRepository {

    TextExtraction save(TextExtraction textExtraction);

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    /**
     * AMQP delivers at least once and {@code document_id} is {@code UNIQUE}: the handler deletes
     * before writing.
     */
    void deleteByDocumentId(UUID documentId);
}
