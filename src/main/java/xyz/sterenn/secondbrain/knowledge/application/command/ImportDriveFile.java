package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * One file of a watched folder, downloaded and handed over. Not {@code UploadDocument}: that one
 * refuses a content the base already holds, where an import attaches its origin to it.
 */
public record ImportDriveFile(UUID ownerId, UUID watchedFolderId, DriveFile file, byte[] content) implements Command {

    @Override
    public String toString() {
        return "ImportDriveFile[ownerId=" + ownerId + ", watchedFolderId=" + watchedFolderId + ", file=" + file.id()
                + ", content=" + content.length + " octets]";
    }
}
