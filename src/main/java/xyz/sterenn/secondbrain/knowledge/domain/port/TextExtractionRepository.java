package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;

/**
 * Port sortant vers le stockage du texte extrait.
 *
 * <p>Aucune méthode ne porte le propriétaire, à l'inverse de {@link DocumentRepository} : un
 * texte se lit toujours par l'identifiant de son document, lequel a déjà été chargé par
 * {@code findByIdAndOwnerId}. Le cloisonnement est fait en amont, il n'a pas à l'être deux
 * fois.
 */
public interface TextExtractionRepository {

    TextExtraction save(TextExtraction textExtraction);

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    /**
     * Efface le texte d'un document, ses blocs avec. Silencieux s'il n'y en a pas.
     *
     * <p>Existe dès ce ticket pour une raison précise : AMQP livre <em>au moins</em> une
     * fois, et {@code document_id} est {@code UNIQUE}. Sans effacement préalable, une
     * redélivrance de {@code DocumentUploaded} ferait échouer l'écriture sur la contrainte,
     * et le document passerait en {@code FAILED} pour un traitement qui avait réussi.
     */
    void deleteByDocumentId(UUID documentId);
}
