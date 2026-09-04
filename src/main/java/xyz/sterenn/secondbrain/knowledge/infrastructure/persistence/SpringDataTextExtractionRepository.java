package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;

interface SpringDataTextExtractionRepository extends JpaRepository<TextExtraction, UUID> {

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    /**
     * Suppression dérivée, et non un {@code @Modifying @Query} : elle charge l'entité avant
     * de la retirer, ce qui laisse Hibernate effacer aussi les blocs de la collection. Une
     * suppression en masse les laisserait orphelins, et la clé étrangère lèverait.
     */
    void deleteByDocumentId(UUID documentId);
}
