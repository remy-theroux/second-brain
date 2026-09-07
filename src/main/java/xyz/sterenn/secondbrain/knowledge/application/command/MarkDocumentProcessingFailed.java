package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/** Dispatched after the processing transaction has been rolled back: see ADR-0028. */
public record MarkDocumentProcessingFailed(UUID documentId, UUID ownerId, String reason) implements Command {}
