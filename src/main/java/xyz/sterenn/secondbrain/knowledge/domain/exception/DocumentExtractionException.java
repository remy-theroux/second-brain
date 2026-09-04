package xyz.sterenn.secondbrain.knowledge.domain.exception;

public abstract class DocumentExtractionException extends DocumentProcessingException {

    protected DocumentExtractionException(String message) {
        super(message);
    }

    protected DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
