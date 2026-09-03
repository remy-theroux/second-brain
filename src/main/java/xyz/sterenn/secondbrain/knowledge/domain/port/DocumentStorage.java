package xyz.sterenn.secondbrain.knowledge.domain.port;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;

/**
 * Port sortant vers la conservation du fichier d'origine.
 *
 * <p>Le domaine ignore où et comment ces octets sont conservés : il exige seulement qu'ils
 * restent récupérables à partir de l'identifiant du document. La promesse s'est encaissée le
 * jour où le support est passé du disque à un stockage objet — le port n'a pas bougé d'une
 * ligne, et aucun des trois appelants non plus. Conserver
 * l'original n'est pas de la nostalgie — c'est ce qui permettra de réextraire un document
 * quand la façon de le découper changera, sans le redemander à l'utilisateur.
 *
 * <p><strong>Les trois méthodes déclarent {@link DocumentStorageUnavailableException}</strong>,
 * et c'est cette déclaration qui justifie que l'exception vive dans
 * {@code domain/exception/} plutôt que chez l'adapter : sans elle, rien ne la rattacherait au
 * contrat, et elle ne serait qu'un détail d'implémentation rangé au mauvais étage. Le domaine
 * sait donc qu'un stockage peut être indisponible ; il ignore toujours par quelle technique.
 * L'indisponibilité n'est pas un refus opposé au document — le même geste, refait plus tard,
 * peut aboutir.
 */
public interface DocumentStorage {

    /**
     * Conserve le contenu sous l'identifiant du document.
     *
     * <p>N'écrase jamais : un identifiant déjà stocké est le signe d'un défaut ailleurs,
     * pas d'un remplacement voulu.
     *
     * @throws DocumentStorageUnavailableException si le stockage n'a pas répondu
     */
    void store(UUID documentId, byte[] content);

    /**
     * Efface le contenu conservé. Silencieux s'il n'y a rien à effacer.
     *
     * @throws DocumentStorageUnavailableException si le stockage n'a pas répondu
     */
    void delete(UUID documentId);

    /**
     * Rend les octets conservés, ou un {@link Optional} vide si rien ne l'est.
     *
     * <p>Aucun cas d'usage ne relit encore un original : cette méthode existe parce que
     * « son fichier d'origine est conservé » est un attendu du ticket, et qu'il ne se
     * vérifie qu'en le relisant <em>par le port</em>. Un test qui irait regarder le
     * support de stockage lui-même vérifierait l'adapter, pas le contrat.
     *
     * @throws DocumentStorageUnavailableException si le stockage n'a pas répondu — à
     *         distinguer de l'{@link Optional} vide, qui dit que le stockage a répondu et
     *         qu'il ne conserve rien sous cet identifiant
     */
    Optional<byte[]> read(UUID documentId);
}
