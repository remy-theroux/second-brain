package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;

/**
 * Outbound port to the keeping of the original file, stored under its document identifier: a
 * write never overwrites, a delete is silent, and a storage that does not answer throws {@link
 * DocumentStorageUnavailableException}.
 */
public interface DocumentStorage {

    void store(UUID documentId, byte[] content);

    void delete(UUID documentId);

    Optional<byte[]> read(UUID documentId);
}
