package xyz.sterenn.secondbrain.shared.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

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
