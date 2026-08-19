package xyz.sterenn.secondbrain.knowledge.domain.exception;

import java.util.UUID;

/**
 * Ce contenu est déjà présent dans la base de connaissance.
 *
 * <p>L'exception porte l'identifiant du document existant : c'est ce qui permet au refus
 * de <em>désigner</em> le doublon plutôt que de se contenter de le signaler. Sans lui,
 * l'appelant saurait qu'il n'a rien créé sans savoir vers quoi se tourner.
 */
public class DuplicateDocumentException extends RuntimeException {

    private final UUID existingDocumentId;

    public DuplicateDocumentException(UUID existingDocumentId) {
        super("Ce contenu est déjà présent dans votre base de connaissance.");
        this.existingDocumentId = existingDocumentId;
    }

    public UUID getExistingDocumentId() {
        return existingDocumentId;
    }
}
