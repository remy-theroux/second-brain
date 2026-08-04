package xyz.sterenn.secondbrain.shared.bus;

/**
 * Aucun handler n'est enregistré pour le message dispatché. Erreur de câblage,
 * pas erreur métier.
 */
public class HandlerNotFoundException extends RuntimeException {

    public HandlerNotFoundException(Class<?> messageType) {
        super("Aucun handler enregistré pour " + messageType.getSimpleName());
    }
}
