package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Mère des deux façons dont l'extraction d'un document peut refuser d'aboutir.
 *
 * <p>Elle reste utile après l'arrivée de {@link DocumentProcessingException} : elle dit
 * <em>quelle</em> étape a refusé, là où sa mère dit seulement qu'un refus est affichable. Ce
 * qui décide de l'affichage, en revanche, c'est la mère — le consommateur d'événements ne
 * teste plus qu'elle.
 *
 * <p>{@code RuntimeException} par sa mère, et non checked : c'est ce qui déclenche le rollback
 * promis par le {@code CommandBus}.
 */
public abstract class DocumentExtractionException extends DocumentProcessingException {

    protected DocumentExtractionException(String message) {
        super(message);
    }

    protected DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
