package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class MissingDocumentContentException extends RuntimeException {

    public static final String MESSAGE = "L'original de ce document n'est plus disponible.";

    public MissingDocumentContentException() {
        super(MESSAGE);
    }
}
