package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class EmbeddingUnavailableException extends DocumentProcessingException {

    public EmbeddingUnavailableException(String message) {
        super(message);
    }

    public EmbeddingUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
