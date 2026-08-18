package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;

/**
 * Port sortant vers le stockage des documents. Le domaine énonce ce dont il a besoin ;
 * l'implémentation vit dans {@code knowledge.infrastructure.persistence}.
 *
 * <p>Chaque méthode porte le propriétaire : une base de connaissance appartient à un
 * compte, et aucune lecture ne doit pouvoir l'oublier par distraction.
 */
public interface DocumentRepository {

    /**
     * @throws DuplicateDocumentException si la contrainte d'unicité (propriétaire,
     *         empreinte) est violée à l'écriture — l'adapter traduit l'erreur technique
     *         en erreur métier
     */
    Document save(Document document);

    Optional<Document> findByOwnerIdAndChecksum(UUID ownerId, Checksum checksum);

    Optional<Document> findByIdAndOwnerId(UUID id, UUID ownerId);

    /** Du plus récent au plus ancien : c'est le dernier dépôt qu'on vient regarder. */
    List<Document> findAllByOwnerId(UUID ownerId);

    void delete(Document document);
}
