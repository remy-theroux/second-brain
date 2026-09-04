package xyz.sterenn.secondbrain.knowledge.domain.exception;

public abstract class DocumentProcessingException extends RuntimeException {

    protected DocumentProcessingException(String message) {
        super(message);
    }

    protected DocumentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
