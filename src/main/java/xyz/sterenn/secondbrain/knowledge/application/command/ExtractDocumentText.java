package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Extraire le texte d'un document déjà déposé.
 *
 * <p>Elle porte le propriétaire autant que le document : le port de lecture exige que
 * « chaque méthode porte le propriétaire, et qu'aucune lecture ne puisse l'oublier par
 * distraction ». {@code DocumentUploaded} porte déjà les deux ; ajouter un {@code findById}
 * non cloisonné pour le confort du worker ouvrirait la seule lecture de la base qui ignore
 * à qui elle appartient.
 */
public record ExtractDocumentText(UUID documentId, UUID ownerId) implements Command {}
