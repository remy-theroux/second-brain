package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;

/**
 * Port sortant vers le stockage du texte extrait : il se lit par l'identifiant de son document,
 * le cloisonnement par propriétaire ayant déjà été fait en amont.
 */
public interface TextExtractionRepository {

    TextExtraction save(TextExtraction textExtraction);

    Optional<TextExtraction> findByDocumentId(UUID documentId);

    /**
     * AMQP livre au moins une fois et {@code document_id} est {@code UNIQUE} : le handler efface
     * avant d'écrire.
     */
    void deleteByDocumentId(UUID documentId);
}
