package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;

/**
 * Outbound port to the keeping of the original file, stored under its document identifier: only
 * a replacement overwrites, a delete is silent, and a storage that does not answer throws {@link
 * DocumentStorageUnavailableException}.
 */
public interface DocumentStorage {

    void store(UUID documentId, byte[] content);

    /**
     * Overwrites the original of a document, unlike {@link #store}, whose refusal to overwrite
     * guards against a handler calling it twice. Writes an original that was not there.
     */
    void replace(UUID documentId, byte[] content);

    void delete(UUID documentId);

    Optional<byte[]> read(UUID documentId);
}
