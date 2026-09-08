package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * The forged, expired or already used return. The three share one message: distinguishing them
 * would turn the callback into an oracle, exactly as for the verification link.
 */
public class InvalidDriveAuthorizationException extends RuntimeException {

    public static final String MESSAGE = "Cette autorisation Google Drive n'est pas valide.";

    public InvalidDriveAuthorizationException() {
        super(MESSAGE);
    }
}
