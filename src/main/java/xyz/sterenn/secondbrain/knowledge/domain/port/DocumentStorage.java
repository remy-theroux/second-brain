package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant vers la conservation du fichier d'origine.
 *
 * <p>Le domaine ignore où et comment ces octets sont conservés : il exige seulement qu'ils
 * restent récupérables à partir de l'identifiant du document. La promesse s'est encaissée le
 * jour où le support est passé du disque à un stockage objet — le port n'a pas bougé d'une
 * ligne, et aucun des trois appelants non plus. Conserver
 * l'original n'est pas de la nostalgie — c'est ce qui permettra de réextraire un document
 * quand la façon de le découper changera, sans le redemander à l'utilisateur.
 */
public interface DocumentStorage {

    void store(UUID documentId, byte[] content);

    void delete(UUID documentId);

    /**
     * Rend les octets conservés, ou un {@link Optional} vide si rien ne l'est.
     *
     * <p>Aucun cas d'usage ne relit encore un original : cette méthode existe parce que
     * « son fichier d'origine est conservé » est un attendu du ticket, et qu'il ne se
     * vérifie qu'en le relisant <em>par le port</em>. Un test qui irait regarder le
     * support de stockage lui-même vérifierait l'adapter, pas le contrat.
     */
    Optional<byte[]> read(UUID documentId);
}
