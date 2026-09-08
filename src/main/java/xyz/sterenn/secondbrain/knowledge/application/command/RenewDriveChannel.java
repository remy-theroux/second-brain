package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/** The subscription of one Drive: renewed if its deadline is near, opened if it has none. */
public record RenewDriveChannel(UUID ownerId) implements Command {}
