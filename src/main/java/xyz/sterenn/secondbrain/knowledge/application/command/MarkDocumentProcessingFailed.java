package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/** Dispatchée après l'annulation de la transaction du traitement : voir ADR-0028. */
public record MarkDocumentProcessingFailed(UUID documentId, UUID ownerId, String reason) implements Command {}
