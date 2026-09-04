package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;

interface SpringDataTextChunkRepository extends JpaRepository<TextChunk, UUID> {

    List<TextChunk> findByDocumentIdOrderByPosition(UUID documentId);

    void deleteByDocumentId(UUID documentId);

    // `position` et `text` heurtent la grammaire de PostgreSQL comme alias, et pgvector
    // n'accepte aucune conversion implicite : d'où les alias préfixés et le CAST explicite.
    @Query(value = """
                    SELECT d.id             AS document_id,
                           d.filename       AS filename,
                           c.chunk_position AS chunk_position,
                           c.heading        AS heading,
                           c.text           AS chunk_text,
                           1 - (c.embedding <=> CAST(:question AS vector)) AS similarity
                    FROM knowledge_text_chunks c
                    JOIN knowledge_documents d ON d.id = c.document_id
                    WHERE d.owner_id = :ownerId
                    ORDER BY c.embedding <=> CAST(:question AS vector)
                    LIMIT :limit
                    """, nativeQuery = true)
    List<ChunkMatchRow> findNearest(
            @Param("ownerId") UUID ownerId, @Param("question") String question, @Param("limit") int limit);
}
