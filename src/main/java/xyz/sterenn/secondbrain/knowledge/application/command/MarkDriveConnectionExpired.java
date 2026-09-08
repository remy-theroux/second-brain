package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/** Dispatched after the transaction that met the revocation has been rolled back: see ADR-0028. */
public record MarkDriveConnectionExpired(UUID ownerId) implements Command {}
