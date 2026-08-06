package xyz.sterenn.secondbrain.shared.bus;

/**
 * Achemine une query vers son handler, de façon synchrone.
 */
public interface QueryBus {

    /**
     * @throws HandlerNotFoundException si aucun handler n'est enregistré pour ce type
     */
    <R> R ask(Query<R> query);
}
