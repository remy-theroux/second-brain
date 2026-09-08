package xyz.sterenn.secondbrain.knowledge.domain.exception;

public class FolderAlreadyCoveredException extends RuntimeException {

    public FolderAlreadyCoveredException(String coveringFolderName) {
        super("Ce dossier est déjà couvert par « " + coveringFolderName + " ».");
    }
}
