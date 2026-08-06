package xyz.sterenn.secondbrain.shared.bus;

/**
 * Traite une et une seule query. Comme les {@link CommandHandler}, ne doit pas être
 * annoté {@code @Transactional} : {@link QueryBus#ask} ouvre déjà une transaction
 * en lecture seule.
 */
public interface QueryHandler<Q extends Query<R>, R> {

    R handle(Q query);
}
