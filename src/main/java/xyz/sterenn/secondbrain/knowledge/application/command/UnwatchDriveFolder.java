package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record UnwatchDriveFolder(UUID ownerId, UUID watchedFolderId) implements Command {}
