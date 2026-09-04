package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant vers la conservation du fichier d'origine, rangé sous l'identifiant de son
 * document : l'écriture n'écrase jamais, l'effacement est silencieux.
 */
public interface DocumentStorage {

    void store(UUID documentId, byte[] content);

    void delete(UUID documentId);

    Optional<byte[]> read(UUID documentId);
}
