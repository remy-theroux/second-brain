package xyz.sterenn.secondbrain.shared.bus;

/**
 * <strong>Never annotate a handler with {@code @Transactional}</strong>: the resulting proxying
 * prevents its generic type from being resolved at startup.
 */
public interface CommandHandler<C extends Command> {

    void handle(C command);
}
