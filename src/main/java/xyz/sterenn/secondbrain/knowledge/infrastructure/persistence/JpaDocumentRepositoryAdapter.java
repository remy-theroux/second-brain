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

/**
 * Adapter du port {@link DocumentRepository}. Aucune exception Spring ne franchit cette
 * classe : ce qui remonte à l'application est déjà du vocabulaire métier.
 */
@Component
public class JpaDocumentRepositoryAdapter implements DocumentRepository {

    private final SpringDataDocumentRepository springDataDocumentRepository;

    JpaDocumentRepositoryAdapter(SpringDataDocumentRepository springDataDocumentRepository) {
        this.springDataDocumentRepository = springDataDocumentRepository;
    }

    /**
     * {@code saveAndFlush} : la traduction de la violation d'unicité doit se faire dans ce
     * {@code try}, or sans flush explicite l'erreur ne surviendrait qu'au commit, hors de
     * portée du {@code catch}.
     *
     * <p>Le handler a déjà écarté le doublon par une lecture ; ce filet ne se referme donc
     * que sur deux dépôts simultanés du même contenu, où les deux lectures passent avant
     * que l'une ait commité. Le refus perd alors sa capacité à désigner le document
     * existant — {@code null} plutôt qu'un identifiant inventé : le perdant de la course
     * apprend qu'il n'a rien créé, et sa liste lui montrera le gagnant.
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
        // La suppression du fichier suit immédiatement, côté handler : sans flush, la ligne
        // partirait au commit, donc après l'effacement de l'original qu'elle désigne.
        springDataDocumentRepository.flush();
    }
}
