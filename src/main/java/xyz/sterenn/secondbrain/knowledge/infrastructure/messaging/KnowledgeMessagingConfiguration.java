package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

@Configuration
public class KnowledgeMessagingConfiguration {

    public static final String KNOWLEDGE_EVENTS_QUEUE = "domain.knowledge.events";

    private static final String KNOWLEDGE_EVENTS_PATTERN = "knowledge.#";

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(
                List.of(DocumentUploaded.class, DocumentTextExtracted.class, DocumentTextIndexed.class));
    }

    /**
     * Déclarée dans les deux rôles, worker compris : l'API démarrée seule publierait sinon
     * dans un exchange sans queue liée, et le message serait perdu sans bruit.
     */
    @Bean
    public Queue knowledgeEventsQueue() {
        return new Queue(KNOWLEDGE_EVENTS_QUEUE, true);
    }

    @Bean
    public Binding knowledgeEventsBinding(Queue knowledgeEventsQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(knowledgeEventsQueue)
                .to(domainEventsExchange)
                .with(KNOWLEDGE_EVENTS_PATTERN);
    }
}
