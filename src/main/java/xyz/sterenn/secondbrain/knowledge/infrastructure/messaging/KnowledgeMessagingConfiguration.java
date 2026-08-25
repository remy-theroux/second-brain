package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

/**
 * Ce que le contexte {@code knowledge} met sur le transport : ses événements, et la queue
 * par laquelle il consomme.
 */
@Configuration
public class KnowledgeMessagingConfiguration {

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(DocumentUploaded.class));
    }
}
