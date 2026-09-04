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
        // durable, non auto-delete : l'exchange survit au redémarrage du broker.
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter domainEventMessageConverter(ObjectProvider<DomainEventRegistration> registrations) {
        List<Class<? extends DomainEvent>> types =
                registrations.stream().flatMap(r -> r.types().stream()).toList();

        // TYPE_ID et non INFERRED (le défaut) : en INFERRED, la réception déduit le type du
        // paramètre du listener et ne consulte jamais l'en-tête __TypeId__ — un
        // `on(DocumentUploaded)` désérialiserait n'importe quel corps en DocumentUploaded.
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(DomainEventNames.mappingOf(types));
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
