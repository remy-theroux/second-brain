package xyz.sterenn.secondbrain.knowledge.domain.exception;

import xyz.sterenn.secondbrain.knowledge.domain.ImportPolicy;

/**
 * Google refuses to export a Doc past its own ceiling, and no listing announces it: the file has
 * no size of its own, so this refusal is the first and only place the ceiling can be met.
 */
public class DocumentTooLargeToExportException extends DriveImportRejectedException {

    public DocumentTooLargeToExportException(Throwable cause) {
        super(ImportPolicy.exportRefusedReason(), cause);
    }
}
