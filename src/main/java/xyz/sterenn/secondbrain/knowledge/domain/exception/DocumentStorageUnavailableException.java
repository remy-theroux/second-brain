package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DocumentStorageUnavailableException extends RuntimeException {

    public DocumentStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
