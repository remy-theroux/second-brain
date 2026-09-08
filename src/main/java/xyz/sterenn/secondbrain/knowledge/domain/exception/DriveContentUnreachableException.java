package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Google hands this file or this folder back no more: deleted, or its sharing withdrawn. It is
 * the everyday accident of a walk that lists before it downloads, and it is not an outage.
 */
public class DriveContentUnreachableException extends DriveImportRejectedException {

    public static final String MESSAGE =
            "Ce contenu n'est plus accessible dans votre Drive : il a été supprimé, ou son partage retiré.";

    public DriveContentUnreachableException() {
        super(MESSAGE);
    }

    public DriveContentUnreachableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
