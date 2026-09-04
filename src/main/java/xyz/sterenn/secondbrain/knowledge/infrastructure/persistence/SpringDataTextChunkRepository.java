package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;

interface SpringDataTextChunkRepository extends JpaRepository<TextChunk, UUID> {

    List<TextChunk> findByDocumentIdOrderByPosition(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}
