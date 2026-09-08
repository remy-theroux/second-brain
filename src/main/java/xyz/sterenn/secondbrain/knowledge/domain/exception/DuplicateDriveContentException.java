package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * A folder holding {@code rapport.pdf} and its copy {@code rapport (1).pdf} brings the same bytes
 * twice: the second file goes nowhere, and it says so rather than vanishing without a trace. The
 * document already holding them may just as well have been uploaded by hand, hence a message that
 * names neither source.
 */
public class DuplicateDriveContentException extends DriveImportRejectedException {

    public static final String MESSAGE = "Ce contenu est déjà présent dans votre base, sous un autre document.";

    public DuplicateDriveContentException() {
        super(MESSAGE);
    }
}
