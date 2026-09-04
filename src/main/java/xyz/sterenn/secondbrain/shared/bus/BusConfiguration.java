package xyz.sterenn.secondbrain.shared.bus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusConfiguration {

    // ObjectProvider et non List injectée : une liste requise vide ferait échouer le
    // démarrage du contexte tant qu'aucun handler n'existe.
    @Bean
    public CommandBus commandBus(ObjectProvider<CommandHandler<?>> handlers) {
        return new SpringCommandBus(handlers.stream().toList());
    }

    @Bean
    public QueryBus queryBus(ObjectProvider<QueryHandler<?, ?>> handlers) {
        return new SpringQueryBus(handlers.stream().toList());
    }
}
