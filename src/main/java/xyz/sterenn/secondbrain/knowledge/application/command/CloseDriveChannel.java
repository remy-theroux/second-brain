package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/** Unsubscribes: a channel left open notifies for a Drive nobody reads any more. */
public record CloseDriveChannel(UUID ownerId) implements Command {}
