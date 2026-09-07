package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ChunkMatch;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;

@Component
public class JpaTextChunkRepositoryAdapter implements TextChunkRepository {

    private final SpringDataTextChunkRepository springDataTextChunkRepository;

    JpaTextChunkRepositoryAdapter(SpringDataTextChunkRepository springDataTextChunkRepository) {
        this.springDataTextChunkRepository = springDataTextChunkRepository;
    }

    @Override
    public List<TextChunk> saveAll(List<TextChunk> textChunks) {
        return springDataTextChunkRepository.saveAllAndFlush(textChunks);
    }

    @Override
    public List<TextChunk> findByDocumentId(UUID documentId) {
        return springDataTextChunkRepository.findByDocumentIdOrderByPosition(documentId);
    }

    /**
     * The handler deletes then writes within the same transaction, and
     * {@code (document_id, chunk_position)} is {@code UNIQUE}: without this flush, Hibernate
     * would order the inserts before the deletes when flushing.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataTextChunkRepository.deleteByDocumentId(documentId);
        springDataTextChunkRepository.flush();
    }

    @Override
    public List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit) {
        return springDataTextChunkRepository.findNearest(ownerId, pgvectorLiteral(question), limit).stream()
                .map(row -> new ChunkMatch(
                        row.getDocumentId(),
                        row.getFilename(),
                        row.getChunkPosition(),
                        new Chunk(row.getHeading(), row.getChunkText()),
                        row.getSimilarity()))
                .toList();
    }

    private static String pgvectorLiteral(Embedding embedding) {
        float[] values = embedding.values();
        StringBuilder literal = new StringBuilder(values.length * 12).append('[');
        for (int dimension = 0; dimension < values.length; dimension++) {
            if (dimension > 0) {
                literal.append(',');
            }
            literal.append(values[dimension]);
        }
        return literal.append(']').toString();
    }
}
