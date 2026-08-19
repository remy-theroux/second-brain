package xyz.sterenn.secondbrain.knowledge.application.query;

import java.util.List;
import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Query;

/**
 * Contenu de la base de connaissance d'un compte, du dépôt le plus récent au plus ancien.
 *
 * <p>Une base vide rend une liste vide, jamais une erreur : n'avoir encore rien déposé est
 * un état parfaitement normal, et c'est même celui de tout nouveau compte.
 *
 * @param ownerId compte propriétaire, tel que le jeton d'accès le désigne
 */
public record ListDocuments(UUID ownerId) implements Query<List<DocumentView>> {}
