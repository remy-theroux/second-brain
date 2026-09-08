package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Where the change feed of a Drive stands. A null {@code pageToken} says the position is lost,
 * which is what a token Google no longer knows leaves behind: a full scan is owed, and starting
 * again from now would let the base drift in silence.
 */
public record RecordDriveChangePosition(UUID ownerId, String pageToken) implements Command {

    public static RecordDriveChangePosition lost(UUID ownerId) {
        return new RecordDriveChangePosition(ownerId, null);
    }
}
