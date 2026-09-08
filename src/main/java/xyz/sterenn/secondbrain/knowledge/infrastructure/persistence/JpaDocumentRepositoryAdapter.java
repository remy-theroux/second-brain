package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

@Component
public class JpaDocumentRepositoryAdapter implements DocumentRepository {

    private final SpringDataDocumentRepository springDataDocumentRepository;

    JpaDocumentRepositoryAdapter(SpringDataDocumentRepository springDataDocumentRepository) {
        this.springDataDocumentRepository = springDataDocumentRepository;
    }

    /**
     * {@code saveAndFlush}: without an explicit flush, the uniqueness violation would only
     * happen at commit, out of reach of the {@code catch}. The duplicate's id is then unknown,
     * hence the {@code null}.
     */
    @Override
    public Document save(Document document) {
        try {
            return springDataDocumentRepository.saveAndFlush(document);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateDocumentException(null);
        }
    }

    @Override
    public Optional<Document> findByOwnerIdAndChecksum(UUID ownerId, Checksum checksum) {
        return springDataDocumentRepository.findByOwnerIdAndChecksum(ownerId, checksum);
    }

    @Override
    public Optional<Document> findByOwnerIdAndDriveFileId(UUID ownerId, String driveFileId) {
        return springDataDocumentRepository.findByOwnerIdAndDriveFileId(ownerId, driveFileId);
    }

    @Override
    public Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return springDataDocumentRepository.findByIdAndOwnerId(id, ownerId);
    }

    @Override
    public List<Document> findAllByOwnerId(UUID ownerId) {
        return springDataDocumentRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    @Override
    public void delete(Document document) {
        springDataDocumentRepository.delete(document);
        // Without a flush, the row would go at commit, hence after the original it points to
        // has been deleted.
        springDataDocumentRepository.flush();
    }
}
