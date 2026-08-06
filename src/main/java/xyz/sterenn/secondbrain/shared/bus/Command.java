package xyz.sterenn.secondbrain.shared.bus;

/**
 * Intention de modifier l'état du système. Une commande ne retourne rien : toute
 * lecture passe par une {@link Query}. À implémenter par des records immuables.
 */
public interface Command {
}
