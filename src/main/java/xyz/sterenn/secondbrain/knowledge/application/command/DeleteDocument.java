package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Retrait d'un document de la base de connaissance.
 *
 * <p>Le propriétaire fait partie de l'intention et non d'un contrôle ajouté après coup :
 * il n'existe pas de « supprimer ce document » dans l'absolu, seulement « supprimer ce
 * document de ma base ».
 *
 * @param documentId identifiant du document à retirer
 * @param ownerId    compte propriétaire, tel que le jeton d'accès le désigne
 */
public record DeleteDocument(UUID documentId, UUID ownerId) implements Command {}
