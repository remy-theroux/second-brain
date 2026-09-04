package xyz.sterenn.secondbrain.knowledge.domain.exception;

import java.util.UUID;

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
