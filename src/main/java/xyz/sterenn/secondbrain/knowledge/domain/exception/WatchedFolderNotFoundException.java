package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class WatchedFolderNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Ce dossier surveillé est introuvable.";

    public WatchedFolderNotFoundException() {
        super(MESSAGE);
    }
}
