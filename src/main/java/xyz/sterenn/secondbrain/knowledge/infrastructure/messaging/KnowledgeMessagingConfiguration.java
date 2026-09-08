package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentContentReplaced;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextExtracted;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentTextIndexed;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveFolderImportRequested;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

@Configuration
public class KnowledgeMessagingConfiguration {

    public static final String KNOWLEDGE_EVENTS_QUEUE = "domain.knowledge.events";

    private static final String KNOWLEDGE_EVENTS_PATTERN = "knowledge.#";

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(
                DocumentUploaded.class,
                DocumentContentReplaced.class,
                DocumentTextExtracted.class,
                DocumentTextIndexed.class,
                DriveFolderImportRequested.class,
                DriveSynchronisationRequested.class));
    }

    /**
     * Declared in both roles, worker included: the API started alone would otherwise publish to
     * an exchange with no bound queue, and the message would be lost silently.
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
