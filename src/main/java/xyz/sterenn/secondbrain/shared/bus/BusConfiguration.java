package xyz.sterenn.secondbrain.shared.bus;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusConfiguration {

    // ObjectProvider rather than an injected List: an empty required list would fail
    // context startup as long as no handler exists.
    @Bean
    public CommandBus commandBus(ObjectProvider<CommandHandler<?>> handlers) {
        return new SpringCommandBus(handlers.stream().toList());
    }

    @Bean
    public QueryBus queryBus(ObjectProvider<QueryHandler<?, ?>> handlers) {
        return new SpringQueryBus(handlers.stream().toList());
    }
}
