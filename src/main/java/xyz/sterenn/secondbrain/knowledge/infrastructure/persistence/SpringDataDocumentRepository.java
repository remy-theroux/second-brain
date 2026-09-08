package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

interface SpringDataDocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByOwnerIdAndChecksum(UUID ownerId, Checksum checksum);

    Optional<Document> findByOwnerIdAndDriveFileId(UUID ownerId, String driveFileId);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Document> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    // The aliases are the projection: an interface projection binds them to its getters by name.
    @Query("""
            SELECT d.watchedFolderId AS watchedFolderId, COUNT(d) AS documentCount
            FROM Document d
            WHERE d.ownerId = :ownerId AND d.watchedFolderId IS NOT NULL
            GROUP BY d.watchedFolderId
            """)
    List<WatchedFolderDocumentCountRow> countByWatchedFolder(@Param("ownerId") UUID ownerId);
}
