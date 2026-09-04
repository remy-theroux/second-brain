package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;

/**
 * Port sortant vers la conservation du fichier d'origine, rangé sous l'identifiant de son
 * document : l'écriture n'écrase jamais, l'effacement est silencieux, et un stockage qui ne
 * répond pas lève {@link DocumentStorageUnavailableException}.
 */
public interface DocumentStorage {

    void store(UUID documentId, byte[] content);

    void delete(UUID documentId);

    Optional<byte[]> read(UUID documentId);
}
