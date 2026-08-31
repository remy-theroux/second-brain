package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Mère des deux façons dont l'extraction d'un document peut refuser d'aboutir.
 *
 * <p>Elle existe pour une raison précise : c'est elle que le consommateur d'événements
 * interroge pour décider si le message d'échec peut être montré à l'utilisateur. Un refus
 * métier porte un message affichable tel quel ; une {@code NullPointerException} n'en porte
 * aucun qu'on puisse afficher. Voir ADR-0028.
 *
 * <p>{@code RuntimeException} et non checked : c'est ce qui déclenche le rollback promis par
 * le {@code CommandBus}.
 */
public abstract class DocumentExtractionException extends RuntimeException {

    protected DocumentExtractionException(String message) {
        super(message);
    }

    protected DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
