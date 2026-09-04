package xyz.sterenn.secondbrain.knowledge.infrastructure.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;

/**
 * Toute {@link IOException} devient une {@link UncheckedIOException} : le rollback promis
 * par le {@code CommandBus} n'a lieu que sur une {@code RuntimeException}. Ce qui est écrit
 * ici y survit malgré tout — voir ADR-0020.
 */
@Component
public class FilesystemDocumentStorage implements DocumentStorage {

    private final Path originalsPath;

    FilesystemDocumentStorage(@Value("${secondbrain.storage.originals-path}") String originalsPath) {
        this.originalsPath = Path.of(originalsPath);
    }

    @Override
    public void store(UUID documentId, byte[] content) {
        Path destination = resolve(documentId);
        try {
            // Dans un conteneur, le volume est monté avant l'application, mais rien ne
            // garantit que le sous-répertoire existe.
            Files.createDirectories(destination.getParent());
            // CREATE_NEW : deux documents ne partagent jamais un identifiant, donc un fichier
            // déjà là révèle un défaut qu'il vaut mieux voir qu'écraser.
            Files.write(destination, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("Un original est déjà conservé pour le document " + documentId, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de conserver l'original du document " + documentId, e);
        }
    }

    @Override
    public void delete(UUID documentId) {
        try {
            Files.deleteIfExists(resolve(documentId));
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible d'effacer l'original du document " + documentId, e);
        }
    }

    @Override
    public Optional<byte[]> read(UUID documentId) {
        Path source = resolve(documentId);
        if (!Files.isRegularFile(source)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(source));
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de relire l'original du document " + documentId, e);
        }
    }

    /** L'identifiant vient de la base, jamais de la requête : pas de traversée de répertoire. */
    private Path resolve(UUID documentId) {
        return originalsPath.resolve(documentId.toString());
    }
}
