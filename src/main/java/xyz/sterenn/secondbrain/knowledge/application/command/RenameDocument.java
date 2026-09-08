package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * The only command of this context without an HTTP route: nothing in the product renames a
 * document by hand, and its single caller is the Drive synchronisation.
 */
public record RenameDocument(UUID ownerId, UUID documentId, String filename) implements Command {}
