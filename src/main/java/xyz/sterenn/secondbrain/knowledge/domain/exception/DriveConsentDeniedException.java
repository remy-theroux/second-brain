package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DriveConsentDeniedException extends RuntimeException {

    public static final String MESSAGE = "L'accès à votre Google Drive n'a pas été accordé.";

    public DriveConsentDeniedException() {
        super(MESSAGE);
    }
}
