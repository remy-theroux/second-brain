package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;

/** Adapter du port {@link TextChunkRepository}. */
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
     * Le flush n'est pas décoratif, et c'est le même piège qu'à l'extraction : le handler
     * efface puis écrit dans la même transaction, et {@code (document_id, chunk_position)}
     * est {@code UNIQUE}. Sans lui, Hibernate ordonnerait les insertions avant les
     * suppressions au moment du vidage, et la contrainte se refermerait sur des lignes que
     * l'on venait justement de retirer.
     */
    @Override
    public void deleteByDocumentId(UUID documentId) {
        springDataTextChunkRepository.deleteByDocumentId(documentId);
        springDataTextChunkRepository.flush();
    }
}
