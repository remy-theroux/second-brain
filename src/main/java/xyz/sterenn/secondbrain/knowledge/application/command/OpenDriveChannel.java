package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/** Subscribes to the push notifications of a connected Drive. */
public record OpenDriveChannel(UUID ownerId) implements Command {}
