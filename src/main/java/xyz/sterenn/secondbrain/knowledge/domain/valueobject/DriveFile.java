package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.time.Instant;

/**
 * A file of a watched folder, in a format this base can read: the walk drops the others rather
 * than handing back what nothing downstream could open. A native Google Doc is one of them under
 * the name and the format of the file it exports into, and it alone carries a workspace type.
 */
public record DriveFile(
        String id,
        String name,
        DocumentFormat format,
        long sizeBytes,
        String webViewLink,
        Instant modifiedTime,
        GoogleWorkspaceType workspaceType) {

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
        // A native Doc holds no bytes of its own, so files.list hands back no size for it.
        if (sizeBytes <= 0 && workspaceType == null) {
            throw new IllegalArgumentException("An empty file has nothing to bring to the knowledge base");
        }
    }

    public static DriveFile downloaded(
            String id, String name, DocumentFormat format, long sizeBytes, String webViewLink, Instant modifiedTime) {
        return new DriveFile(id, name, format, sizeBytes, webViewLink, modifiedTime, null);
    }

    public static DriveFile exported(
            GoogleWorkspaceType workspaceType, String id, String name, String webViewLink, Instant modifiedTime) {
        DocumentFormat format = workspaceType
                .exportedFormat()
                .orElseThrow(() -> new IllegalArgumentException(
                        "This type of Google Workspace file does not export: " + workspaceType));
        return new DriveFile(id, workspaceType.exportedName(name), format, 0, webViewLink, modifiedTime, workspaceType);
    }

    public boolean isExported() {
        return workspaceType != null;
    }

    public DriveProvenance provenance() {
        return new DriveProvenance(id, webViewLink, modifiedTime);
    }
}
