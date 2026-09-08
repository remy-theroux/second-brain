package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Instant;

/**
 * The Drive file a document came from. The link and the modification time are what Drive
 * happened to hand back: neither is worth losing a file over, only the identifier is.
 */
public record DriveProvenance(String fileId, String webViewLink, Instant modifiedTime) {

    public static final int MAX_FILE_ID_LENGTH = 255;

    public DriveProvenance {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("The Drive identifier of an imported file is required");
        }
        fileId = fileId.trim();
    }
}
