package xyz.sterenn.secondbrain.shared.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bus synchrone : la table de routage est construite une fois au démarrage, en
 * résolvant le paramètre générique de chaque handler.
 *
 * <p><strong>La transaction SQL vit ici.</strong> {@code dispatch} est annoté
 * {@code @Transactional} : tout ce que le handler déclenche — lectures, écritures,
 * appels à d'autres composants — s'exécute dans une seule transaction, et la moindre
 * {@link RuntimeException} annule l'ensemble. (Rappel Spring : une exception
 * <em>checked</em> ne déclenche pas de rollback par défaut ; les exceptions métier du
 * projet héritent donc toutes de {@code RuntimeException}.)
 */
public class SpringCommandBus implements CommandBus {

    private final Map<Class<?>, CommandHandler<?>> handlers = new HashMap<>();

    public SpringCommandBus(List<CommandHandler<?>> discoveredHandlers) {
        for (CommandHandler<?> handler : discoveredHandlers) {
            Class<?> commandType = commandTypeOf(handler);
            CommandHandler<?> previous = handlers.put(commandType, handler);
            if (previous != null) {
                throw new IllegalStateException("Deux handlers déclarés pour la commande " + commandType.getSimpleName()
                        + " : " + previous.getClass().getName() + " et "
                        + handler.getClass().getName());
            }
        }
    }

    // Type brut assumé : la table de routage est hétérogène, et le typage est garanti
    // par construction (la clé est la classe de commande que le handler déclare traiter).
    @Override
    @Transactional
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void dispatch(Command command) {
        CommandHandler handler = handlers.get(command.getClass());
        if (handler == null) {
            throw new HandlerNotFoundException(command.getClass());
        }
        handler.handle(command);
    }

    private static Class<?> commandTypeOf(CommandHandler<?> handler) {
        Class<?>[] arguments = GenericTypeResolver.resolveTypeArguments(handler.getClass(), CommandHandler.class);
        if (arguments == null || arguments.length != 1) {
            throw new IllegalStateException(
                    handler.getClass().getName() + " doit implémenter CommandHandler avec un type concret");
        }
        return arguments[0];
    }
}
