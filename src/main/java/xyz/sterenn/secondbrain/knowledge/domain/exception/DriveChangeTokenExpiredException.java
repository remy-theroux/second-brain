package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * Google no longer knows the kept page token. What is owed is a full scan of the watched
 * folders: starting again from now would let the base drift in silence.
 */
public class DriveChangeTokenExpiredException extends RuntimeException {

    public static final String MESSAGE = "Le suivi des changements du Drive a été repris depuis le début.";

    public DriveChangeTokenExpiredException() {
        super(MESSAGE);
    }

    public DriveChangeTokenExpiredException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
