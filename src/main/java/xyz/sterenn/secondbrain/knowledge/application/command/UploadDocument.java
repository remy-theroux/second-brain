package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record UploadDocument(UUID ownerId, String filename, byte[] content) implements Command {

    @Override
    public String toString() {
        return "UploadDocument[ownerId=" + ownerId + ", filename=" + filename + ", content=" + content.length
                + " octets]";
    }
}
