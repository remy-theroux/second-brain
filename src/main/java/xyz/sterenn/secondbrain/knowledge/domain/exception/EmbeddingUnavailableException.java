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
 * <p>{@code RuntimeException} par sa mère, et non checked : c'est ce qui déclenche le rollback
 * promis par le {@code CommandBus}. Et {@link DocumentProcessingException} pour mère, et non
 * {@code DocumentExtractionException} : {@code KnowledgeEventListener.motif()} ne montre que
 * les messages de cette famille, et sans ce lien de parenté le message soigné ci-dessus serait
 * écrasé par le motif générique du listener — exactement ce qu'il existe pour éviter.
 */
public class EmbeddingUnavailableException extends DocumentProcessingException {

    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
