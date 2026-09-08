package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Instant;

/**
 * A file of a watched folder, in a format this base can read: the walk drops the others rather
 * than handing back what nothing downstream could open.
 */
public record DriveFile(
        String id, String name, DocumentFormat format, long sizeBytes, String webViewLink, Instant modifiedTime) {

    public DriveFile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("The Drive identifier of a file is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("The name of a Drive file is required");
        }
        if (format == null) {
            throw new IllegalArgumentException("The format of a Drive file is required");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("An empty file has nothing to bring to the knowledge base");
        }
    }

    public DriveProvenance provenance() {
        return new DriveProvenance(id, webViewLink, modifiedTime);
    }
}
