package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * A folder holding {@code rapport.pdf} and its copy {@code rapport (1).pdf} brings the same bytes
 * twice: the second file goes nowhere, and it says so rather than vanishing without a trace.
 */
public class DuplicateDriveContentException extends DriveImportRejectedException {

    public static final String MESSAGE = "Ce contenu est déjà dans votre base, importé depuis un autre fichier Drive.";

    public DuplicateDriveContentException() {
        super(MESSAGE);
    }
}
