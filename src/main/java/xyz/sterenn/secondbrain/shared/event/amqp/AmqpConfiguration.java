package xyz.sterenn.secondbrain.shared.event.amqp;

import java.util.List;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.shared.event.DomainEvent;

@Configuration
public class AmqpConfiguration {

    public static final String EVENTS_EXCHANGE = "domain.events";

    @Bean
    public TopicExchange domainEventsExchange() {
        // durable, not auto-delete: the exchange survives a broker restart.
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter domainEventMessageConverter(ObjectProvider<DomainEventRegistration> registrations) {
        List<Class<? extends DomainEvent>> types =
                registrations.stream().flatMap(r -> r.types().stream()).toList();

        // TYPE_ID rather than INFERRED (the default): with INFERRED, reception infers the type
        // from the listener parameter and never reads the __TypeId__ header — an
        // `on(DocumentUploaded)` would deserialise any body into DocumentUploaded.
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(DomainEventNames.mappingOf(types));
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
