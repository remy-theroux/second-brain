package xyz.sterenn.secondbrain.shared.bus;

/**
 * <strong>Ne jamais annoter un handler avec {@code @Transactional}</strong> : la proxification
 * qui en résulte empêche la résolution de ses types génériques au démarrage.
 */
public interface QueryHandler<Q extends Query<R>, R> {

    R handle(Q query);
}
