package xyz.sterenn.secondbrain.knowledge.domain.exception;

/**
 * One file left out of an import, for a reason its owner reads next to the file name. The walk
 * goes on: only an unreachable Drive or a withdrawn authorization stops it.
 */
public abstract class DriveImportRejectedException extends RuntimeException {

    protected DriveImportRejectedException(String message) {
        super(message);
    }

    protected DriveImportRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
