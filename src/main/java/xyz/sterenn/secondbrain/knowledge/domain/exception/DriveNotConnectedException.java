package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DriveNotConnectedException extends RuntimeException {

    public static final String MESSAGE = "Connectez un compte Google pour parcourir votre Drive.";

    public DriveNotConnectedException() {
        super(MESSAGE);
    }
}
