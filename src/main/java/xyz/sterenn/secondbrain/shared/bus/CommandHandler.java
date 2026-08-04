package xyz.sterenn.secondbrain.shared.bus;

/**
 * Traite une et une seule commande. Un handler est un bean Spring sans état.
 *
 * <p><strong>Ne jamais annoter un handler avec {@code @Transactional}</strong> : la
 * transaction est portée par {@link CommandBus#dispatch}, et la proxification du
 * handler empêcherait la résolution de son type générique au démarrage.
 */
public interface CommandHandler<C extends Command> {

    void handle(C command);
}
