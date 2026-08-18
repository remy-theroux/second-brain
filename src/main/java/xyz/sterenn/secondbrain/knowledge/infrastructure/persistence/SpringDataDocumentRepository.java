package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

/**
 * Détail Spring Data, volontairement package-private : rien hors de ce package ne doit
 * en dépendre.
 */
interface SpringDataDocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByOwnerIdAndChecksum(UUID ownerId, Checksum checksum);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Document> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
