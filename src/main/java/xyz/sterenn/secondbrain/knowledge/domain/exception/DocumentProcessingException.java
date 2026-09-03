package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Mère de tous les refus qui peuvent interrompre le <strong>traitement</strong> d'un document,
 * quelle qu'en soit l'étape — extraction du texte hier, vectorisation aujourd'hui.
 *
 * <p>Elle existe pour une raison précise, et une seule : c'est elle que le consommateur
 * d'événements interroge pour décider si le message d'échec peut être montré à l'utilisateur.
 * Un refus métier porte un message affichable tel quel ; une {@code NullPointerException} n'en
 * porte aucun qu'on puisse afficher. Voir ADR-0028.
 *
 * <p>Elle a remplacé {@link DocumentExtractionException} dans ce rôle quand la vectorisation
 * est arrivée : « extraction » nommait une phase qui n'est plus la seule, et une URL Ollama
 * mal saisie se serait affichée avec le motif générique, indiscernable d'un PDF illisible.
 *
 * <p>{@code RuntimeException} et non checked : c'est ce qui déclenche le rollback promis par
 * le {@code CommandBus}.
 */
public abstract class DocumentProcessingException extends RuntimeException {

    protected DocumentProcessingException(String message) {
        super(message);
    }

    protected DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
