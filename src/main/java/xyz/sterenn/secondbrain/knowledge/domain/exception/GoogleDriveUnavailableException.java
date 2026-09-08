package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class GoogleDriveUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Google Drive est momentanément injoignable, réessayez plus tard.";

    public GoogleDriveUnavailableException() {
        super(MESSAGE);
    }

    public GoogleDriveUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
