package xyz.sterenn.secondbrain.shared.bus;

/**
 * <strong>Never annotate a handler with {@code @Transactional}</strong>: the resulting proxying
 * prevents its generic types from being resolved at startup.
 */
public interface QueryHandler<Q extends Query<R>, R> {

    R handle(Q query);
}
