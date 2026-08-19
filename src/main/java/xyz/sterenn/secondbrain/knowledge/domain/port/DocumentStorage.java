package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant vers la conservation du fichier d'origine.
 *
 * <p>Le domaine ignore qu'il s'agit d'un système de fichiers : il exige seulement que les
 * octets déposés restent récupérables à partir de l'identifiant du document. Conserver
 * l'original n'est pas de la nostalgie — c'est ce qui permettra de réextraire un document
 * quand la façon de le découper changera, sans le redemander à l'utilisateur.
 */
public interface DocumentStorage {

    /**
     * Conserve le contenu sous l'identifiant du document.
     *
     * <p>N'écrase jamais : un identifiant déjà stocké est le signe d'un défaut ailleurs,
     * pas d'un remplacement voulu.
     */
    void store(UUID documentId, byte[] content);

    /** Efface le contenu conservé. Silencieux s'il n'y a rien à effacer. */
    void delete(UUID documentId);

    /**
     * Rend les octets conservés, ou un {@link Optional} vide si rien ne l'est.
     *
     * <p>Aucun cas d'usage ne relit encore un original : cette méthode existe parce que
     * « son fichier d'origine est conservé » est un attendu du ticket, et qu'il ne se
     * vérifie qu'en le relisant <em>par le port</em>. Un test qui irait regarder le
     * système de fichiers vérifierait l'adapter, pas le contrat.
     */
    Optional<byte[]> read(UUID documentId);
}
