package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;

/**
 * Port sortant vers le stockage des extraits vectorisés.
 *
 * <p>Aucune méthode ne porte le propriétaire, comme {@link TextExtractionRepository} : un
 * extrait se lit toujours par l'identifiant de son document, lequel a déjà été chargé par
 * {@code findByIdAndOwnerId}. Le cloisonnement est fait en amont, il n'a pas à l'être deux
 * fois.
 *
 * <p>Aucune méthode de recherche par similarité : ce ticket écrit l'index, il ne l'interroge
 * pas. C'est RAG-8 qui ajoutera la requête, et elle n'a pas à être devinée d'avance.
 */
public interface TextChunkRepository {

    List<TextChunk> saveAll(List<TextChunk> textChunks);

    /** Dans l'ordre du document. */
    List<TextChunk> findByDocumentId(UUID documentId);

    /**
     * Efface les extraits d'un document. Silencieux s'il n'y en a pas.
     *
     * <p>Même raison qu'à l'extraction : AMQP livre <em>au moins</em> une fois, et
     * {@code (document_id, chunk_position)} est {@code UNIQUE}. Sans effacement préalable,
     * une redélivrance ferait échouer l'écriture sur la contrainte, et le document passerait
     * en {@code FAILED} pour un traitement qui avait réussi. RAG-7 s'en servira pour la
     * réextraction, sans avoir à la créer.
     */
    void deleteByDocumentId(UUID documentId);
}
