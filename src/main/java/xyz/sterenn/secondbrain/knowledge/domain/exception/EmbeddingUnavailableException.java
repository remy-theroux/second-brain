package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Le service de vectorisation n'a pas rendu ce qu'on lui demandait.
 *
 * <p>Son message est <strong>affichable tel quel</strong>, et il nomme la vectorisation. Ce
 * n'est pas de la coquetterie : il n'y a aucun contrôle au démarrage sur la disponibilité du
 * service, donc une URL fausse ou un nom de modèle mal orthographié ne se voient qu'au
 * premier document traité. Sans un message qui désigne le bon coupable, l'utilisateur lirait
 * un motif générique et chercherait du côté de son fichier.
 *
 * <p>Elle couvre trois pannes, parce qu'elles appellent le même geste — regarder la
 * configuration et le service, pas le document : le service injoignable ou en erreur après
 * trois tentatives, une réponse qui ne rend pas autant de vecteurs que de textes, et un
 * vecteur d'une dimension étrangère au modèle attendu.
 *
 * <p>{@code RuntimeException} et non checked : c'est ce qui déclenche le rollback promis par
 * le {@code CommandBus}. Elle prendra pour parent {@code DocumentProcessingException} dans le
 * livrable suivant, quand un second consommateur en aura besoin.
 */
public class EmbeddingUnavailableException extends RuntimeException {

    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
