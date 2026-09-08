package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DriveAuthorizationRevokedException extends RuntimeException {

    public static final String MESSAGE =
            "L'accès à votre Drive a été retiré depuis votre compte Google. Reconnectez-le pour continuer.";

    public DriveAuthorizationRevokedException() {
        super(MESSAGE);
    }

    public DriveAuthorizationRevokedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
