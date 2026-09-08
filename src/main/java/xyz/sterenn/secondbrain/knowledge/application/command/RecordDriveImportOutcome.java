package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportRejection;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;
import xyz.sterenn.secondbrain.shared.bus.Command;

public record RecordDriveImportOutcome(
        UUID ownerId,
        UUID watchedFolderId,
        DriveImportStatus status,
        String error,
        List<DriveImportRejection> rejections)
        implements Command {}
