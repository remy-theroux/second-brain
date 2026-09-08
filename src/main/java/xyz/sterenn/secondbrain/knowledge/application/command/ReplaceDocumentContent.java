package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record ReplaceDocumentContent(UUID ownerId, UUID documentId, String filename, byte[] content)
        implements Command {

    @Override
    public String toString() {
        return "ReplaceDocumentContent[ownerId=" + ownerId + ", documentId=" + documentId + ", filename=" + filename
                + ", content=" + content.length + " octets]";
    }
}
