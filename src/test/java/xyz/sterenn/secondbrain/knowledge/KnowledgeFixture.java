package xyz.sterenn.secondbrain.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Outillage commun aux tests du contexte {@code knowledge}.
 *
 * <p>Les jetons ne sont pas simulés : ils sont émis par le port réel, comme le fait la
 * route de connexion. Un jeton fabriqué à la main passerait à côté de la seule chose que
 * ces tests ont besoin de croire — que le filtre resource server reconnaît bien le porteur.
 */
public final class KnowledgeFixture {

    private KnowledgeFixture() {
        // classe utilitaire
    }

    /** Un jeton valide une heure pour ce compte. */
    public static String jeton(AccessTokenIssuer accessTokenIssuer, UUID compte) {
        Instant maintenant = Instant.now();
        return accessTokenIssuer
                .issue(compte, maintenant, maintenant.plus(Duration.ofHours(1)))
                .value();
    }

    /**
     * Efface les originaux écrits par un test.
     *
     * <p>{@code @Transactional} annule la base, jamais le disque : sans ce nettoyage, un
     * test laisserait derrière lui des fichiers que plus aucune ligne ne désigne, et le
     * refus d'écrasement de l'adapter finirait par faire échouer une exécution ultérieure.
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
}
