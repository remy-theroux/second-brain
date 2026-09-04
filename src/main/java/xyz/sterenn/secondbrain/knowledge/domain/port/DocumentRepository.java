package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

/**
 * Port sortant vers le stockage des documents : toute lecture porte le propriétaire, et la
 * liste vient du plus récent au plus ancien.
 */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findByOwnerIdAndChecksum(UUID ownerId, Checksum checksum);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Document> findAllByOwnerId(UUID ownerId);

    void delete(Document document);
}
