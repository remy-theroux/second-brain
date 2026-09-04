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
     * Le handler efface puis écrit dans la même transaction, et
     * {@code (document_id, chunk_position)} est {@code UNIQUE} : sans ce flush, Hibernate
     * ordonnerait les insertions avant les suppressions au moment du vidage.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataTextChunkRepository.deleteByDocumentId(documentId);
        springDataTextChunkRepository.flush();
    }

    @Override
    public List<ChunkMatch> findNearest(UUID ownerId, Embedding question, int limit) {
        return springDataTextChunkRepository.findNearest(ownerId, litteralPgvector(question), limit).stream()
                .map(ligne -> new ChunkMatch(
                        ligne.getDocumentId(),
                        ligne.getFilename(),
                        ligne.getChunkPosition(),
                        new Chunk(ligne.getHeading(), ligne.getChunkText()),
                        ligne.getSimilarity()))
                .toList();
    }

    private static String litteralPgvector(Embedding embedding) {
        float[] valeurs = embedding.values();
        StringBuilder litteral = new StringBuilder(valeurs.length * 12).append('[');
        for (int dimension = 0; dimension < valeurs.length; dimension++) {
            if (dimension > 0) {
                litteral.append(',');
            }
            litteral.append(valeurs[dimension]);
        }
        return litteral.append(']').toString();
    }
}
