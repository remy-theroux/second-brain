package xyz.sterenn.secondbrain.shared.bus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.GenericTypeResolver;
import org.springframework.transaction.annotation.Transactional;

public class SpringQueryBus implements QueryBus {

    private final Map<Class<?>, QueryHandler<?, ?>> handlers = new HashMap<>();

    public SpringQueryBus(List<QueryHandler<?, ?>> discoveredHandlers) {
        for (QueryHandler<?, ?> handler : discoveredHandlers) {
            Class<?> queryType = queryTypeOf(handler);
            QueryHandler<?, ?> previous = handlers.put(queryType, handler);
            if (previous != null) {
                throw new IllegalStateException("Deux handlers déclarés pour la query " + queryType.getSimpleName()
                        + " : " + previous.getClass().getName() + " et "
                        + handler.getClass().getName());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> R ask(Query<R> query) {
        QueryHandler handler = handlers.get(query.getClass());
        if (handler == null) {
            throw new HandlerNotFoundException(query.getClass());
        }
        return (R) handler.handle(query);
    }

    private static Class<?> queryTypeOf(QueryHandler<?, ?> handler) {
        Class<?>[] arguments = GenericTypeResolver.resolveTypeArguments(handler.getClass(), QueryHandler.class);
        if (arguments == null || arguments.length != 2) {
            throw new IllegalStateException(
                    handler.getClass().getName() + " doit implémenter QueryHandler avec des types concrets");
        }
        return arguments[0];
    }
}
