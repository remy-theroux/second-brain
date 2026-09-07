package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;

interface SpringDataTextExtractionRepository extends JpaRepository<TextExtraction, UUID> {

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    /**
     * A derived delete, not a {@code @Modifying @Query}: it loads the entity before removing it,
     * which lets Hibernate also delete the blocks of the collection. A bulk delete would leave
     * them orphaned, and the foreign key would raise.
     */
    void deleteByDocumentId(UUID documentId);
}
