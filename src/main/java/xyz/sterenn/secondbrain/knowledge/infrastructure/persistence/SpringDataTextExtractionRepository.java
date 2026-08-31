package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit en
 * dépendre.
 */
interface SpringDataTextExtractionRepository extends JpaRepository<TextExtraction, UUID> {

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    /**
     * Suppression dérivée, et non un {@code @Modifying @Query} : elle charge l'entité avant
     * de la retirer, ce qui laisse Hibernate effacer aussi les lignes de la collection.
     * Une requête de suppression en masse court-circuiterait la collection et laisserait des
     * blocs orphelins — la clé étrangère les rattraperait, mais en levant une erreur.
     */
    void deleteByDocumentId(UUID documentId);
}
