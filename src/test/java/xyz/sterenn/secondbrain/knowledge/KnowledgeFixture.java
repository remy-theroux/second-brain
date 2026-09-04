package xyz.sterenn.secondbrain.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

public final class KnowledgeFixture {

    private KnowledgeFixture() {}

    public static String jeton(AccessTokenIssuer accessTokenIssuer, UUID compte) {
        Instant maintenant = Instant.now();
        return accessTokenIssuer
                .issue(compte, maintenant, maintenant.plus(Duration.ofHours(1)))
                .value();
    }

    /**
     * {@code @Transactional} annule la base, jamais le disque : sans ce nettoyage, un original
     * survit et le refus d'écrasement de l'adapter fait échouer une exécution ultérieure.
     */
    public static void videLesOriginaux(String chemin) {
        Path repertoire = Path.of(chemin);
        if (!Files.isDirectory(repertoire)) {
            return;
        }
        try (var contenu = Files.walk(repertoire)) {
            contenu.sorted(Comparator.reverseOrder()).forEach(fichier -> {
                try {
                    Files.deleteIfExists(fichier);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Embedding unVecteur(float valeur) {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        Arrays.fill(valeurs, valeur);
        return Embedding.of(valeurs);
    }
}
