package xyz.sterenn.secondbrain.knowledge.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

public record DriveFolderImportRequested(UUID watchedFolderId, UUID ownerId, Instant occurredAt)
        implements DomainEvent {

    public DriveFolderImportRequested {
        Objects.requireNonNull(watchedFolderId, "The watched folder id is required");
        Objects.requireNonNull(ownerId, "The folder owner is required");
        Objects.requireNonNull(occurredAt, "The event instant is required");
    }
}
