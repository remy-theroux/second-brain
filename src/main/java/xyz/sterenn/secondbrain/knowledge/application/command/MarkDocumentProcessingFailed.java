package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Consigner qu'un <strong>traitement</strong> a échoué, et pourquoi. « Traitement » et non
 * « extraction » : la même commande consigne l'échec d'une vectorisation.
 *
 * <p>Une commande à part, et non un appel dans le handler d'extraction : elle est dispatchée
 * <strong>après</strong> que la transaction de l'extraction a été annulée, donc dans une
 * transaction à elle. Un {@code markExtractionFailed} écrit dans la transaction annulée
 * disparaîtrait avec elle, et le document resterait éternellement en attente — voir
 * ADR-0028.
 *
 * <p>{@code reason} est <strong>affichable tel quel</strong> : c'est l'appelant qui garantit
 * qu'aucune trace technique n'y voyage. Voir {@code KnowledgeEventListener}.
 */
public record MarkDocumentProcessingFailed(UUID documentId, UUID ownerId, String reason) implements Command {}
