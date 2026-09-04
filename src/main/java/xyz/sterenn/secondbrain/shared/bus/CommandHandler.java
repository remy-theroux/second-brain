package xyz.sterenn.secondbrain.shared.bus;

/**
 * <strong>Ne jamais annoter un handler avec {@code @Transactional}</strong> : la proxification
 * qui en résulte empêche la résolution de son type générique au démarrage.
 */
public interface CommandHandler<C extends Command> {

    void handle(C command);
}
