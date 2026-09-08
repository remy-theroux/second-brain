package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record RequestDriveFolderImport(UUID ownerId, UUID watchedFolderId) implements Command {}
