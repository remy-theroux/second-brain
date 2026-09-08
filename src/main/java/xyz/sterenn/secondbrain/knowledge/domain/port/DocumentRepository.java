package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

/**
 * Outbound port to document storage: every read carries the owner, and the list comes newest
 * first.
 */
public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findByOwnerIdAndChecksum(UUID ownerId, Checksum checksum);

    /** Empty when no Drive file of this owner ever brought a document. */
    Optional<Document> findByOwnerIdAndDriveFileId(UUID ownerId, String driveFileId);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Document> findAllByOwnerId(UUID ownerId);

    void delete(Document document);
}
