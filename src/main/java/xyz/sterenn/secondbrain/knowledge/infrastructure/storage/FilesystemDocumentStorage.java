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
 * Adapter système de fichiers du port {@link DocumentStorage}. Un fichier par document,
 * nommé par son identifiant : le nom d'origine ne sert qu'à l'affichage et vit en base,
 * ce qui évite d'avoir à assainir une chaîne venue de l'utilisateur avant d'en faire un
 * chemin.
 *
 * <p>Toute {@link IOException} devient une {@link UncheckedIOException} : le rollback promis
 * par le {@code CommandBus} n'a lieu que sur une {@code RuntimeException}, une exception
 * checked laisserait committer une ligne dont l'original n'a pas été écrit.
 *
 * <p><strong>Un système de fichiers ne participe à aucune transaction.</strong> Ce que
 * cette classe écrit survit à un rollback survenu après elle — écart assumé documenté dans
 * CLAUDE.md.
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
            // Créé ici et non au démarrage : dans un conteneur, le volume est monté avant
            // l'application, mais rien ne garantit que le sous-répertoire existe.
            Files.createDirectories(destination.getParent());
            // CREATE_NEW plutôt que le défaut : deux documents ne partagent jamais un
            // identifiant, donc un fichier déjà là révèle un défaut qu'il vaut mieux voir.
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

    /**
     * L'identifiant est un UUID produit par la base, jamais une chaîne venue de la requête :
     * il n'y a donc pas de traversée de répertoire à craindre ici. La conversion explicite
     * en {@code String} le rappelle plutôt qu'elle ne le corrige.
     */
    private Path resolve(UUID documentId) {
        return originalsPath.resolve(documentId.toString());
    }
}
