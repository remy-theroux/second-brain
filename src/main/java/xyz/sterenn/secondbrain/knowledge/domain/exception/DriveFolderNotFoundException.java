package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class DriveFolderNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Ce dossier est introuvable dans votre Drive.";

    public DriveFolderNotFoundException() {
        super(MESSAGE);
    }
}
