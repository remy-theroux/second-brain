package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import java.util.UUID;

/**
 * Corps d'un refus de doublon. Il ne se contente pas de dire non : il désigne le document
 * déjà présent, pour que l'appelant puisse y renvoyer son utilisateur plutôt que de le
 * laisser chercher.
 *
 * <p>{@code ErrorResponse} n'aurait pas suffi — il ne porte qu'un message, et un
 * identifiant noyé dans une phrase ne s'exploite pas.
 *
 * @param message            message affichable tel quel
 * @param existingDocumentId identifiant du document déjà présent, {@code null} dans le seul
 *                           cas où deux dépôts simultanés du même contenu se sont croisés
 */
public record DuplicateDocumentResponse(String message, UUID existingDocumentId) {}
