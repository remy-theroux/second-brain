package xyz.sterenn.secondbrain.shared.bus;

/**
 * Achemine une commande vers son handler, de façon synchrone et transactionnelle.
 */
public interface CommandBus {

    /**
     * @throws HandlerNotFoundException si aucun handler n'est enregistré pour ce type
     */
    void dispatch(Command command);
}
