package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventNames;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

/**
 * Ce que le contexte {@code knowledge} met sur le transport : ses événements, et la queue
 * par laquelle il consomme.
 *
 * <p>La queue est déclarée dans les deux rôles, pas seulement dans le worker : Spring AMQP
 * déclare à la première connexion, les déclarations sont idempotentes, et l'API démarrée
 * seule ne doit pas publier dans un exchange sans queue liée — le message serait perdu sans
 * bruit. Durable : elle survit au redémarrage du broker, avec ses messages non consommés.
 */
@Configuration
public class KnowledgeMessagingConfiguration {

    public static final String EXTRACTION_QUEUE = "knowledge.extraction";

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(DocumentUploaded.class));
    }

    @Bean
    public Queue extractionQueue() {
        return new Queue(EXTRACTION_QUEUE, true);
    }

    @Bean
    public Binding extractionBinding(Queue extractionQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(extractionQueue)
                .to(domainEventsExchange)
                .with(DomainEventNames.of(DocumentUploaded.class));
    }
}
