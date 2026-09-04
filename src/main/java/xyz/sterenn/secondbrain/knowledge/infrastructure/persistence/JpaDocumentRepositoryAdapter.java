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
     * {@code saveAndFlush} : sans flush explicite, la violation d'unicité ne surviendrait
     * qu'au commit, hors de portée du {@code catch}. L'identifiant du doublon est alors
     * inconnu, d'où le {@code null}.
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
        // Sans flush, la ligne partirait au commit, donc après l'effacement de l'original
        // qu'elle désigne.
        springDataDocumentRepository.flush();
    }
}
