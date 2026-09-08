package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DriveConnectionNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Aucun compte Google Drive n'est connecté.";

    public DriveConnectionNotFoundException() {
        super(MESSAGE);
    }
}
