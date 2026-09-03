package xyz.sterenn.secondbrain.knowledge.application.command;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Découper le texte extrait d'un document, le vectoriser et le ranger.
 *
 * <p>« Indexer » couvre les trois gestes en un mot, là où un nom qui les énumérerait
 * (« découper puis vectoriser puis… ») deviendrait faux au premier changement d'ordre.
 *
 * <p>Le propriétaire voyage avec le document, comme pour toutes les commandes de ce contexte :
 * le cloisonnement ne se relâche pas parce qu'on est dans un worker.
 */
public record IndexDocumentText(UUID documentId, UUID ownerId) implements Command {}
