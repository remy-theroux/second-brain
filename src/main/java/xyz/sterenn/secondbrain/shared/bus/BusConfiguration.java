package xyz.sterenn.secondbrain.shared.bus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage des deux bus.
 *
 * <p>Les handlers sont collectés via {@link ObjectProvider} et non via un
 * {@code List<...>} injecté : une liste requise vide fait échouer le démarrage du
 * contexte, ce qui interdirait de démarrer l'application tant qu'aucun handler n'existe.
 *
 * <p>Les bus sont déclarés en {@code @Bean} plutôt qu'en {@code @Component} pour que
 * leur constructeur reste utilisable tel quel dans les tests unitaires.
 */
@Configuration
public class BusConfiguration {

    @Bean
    public CommandBus commandBus(ObjectProvider<CommandHandler<?>> handlers) {
        return new SpringCommandBus(handlers.stream().toList());
    }

    @Bean
    public QueryBus queryBus(ObjectProvider<QueryHandler<?, ?>> handlers) {
        return new SpringQueryBus(handlers.stream().toList());
    }
}
