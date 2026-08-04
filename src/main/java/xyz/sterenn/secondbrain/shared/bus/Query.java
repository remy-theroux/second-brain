package xyz.sterenn.secondbrain.shared.bus;

/**
 * Demande de lecture. Le paramètre {@code R} porte le type de retour, ce qui permet
 * à {@link QueryBus#ask} d'être typé sans cast côté appelant.
 *
 * @param <R> type du résultat
 */
public interface Query<R> {
}
