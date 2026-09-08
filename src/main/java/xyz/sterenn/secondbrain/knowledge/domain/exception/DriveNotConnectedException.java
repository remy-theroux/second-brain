package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DriveNotConnectedException extends RuntimeException {

    public static final String MESSAGE = "Connectez un compte Google pour accéder à votre Drive.";

    public DriveNotConnectedException() {
        super(MESSAGE);
    }
}
