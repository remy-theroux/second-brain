package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.shared.event.amqp.DomainEventRegistration;

/**
 * Ce que le contexte {@code knowledge} met sur le transport : ses événements, et la queue
 * par laquelle il consomme.
 *
 * <p>La queue est celle du <em>contexte</em>, pas d'un consommateur : liée sur
 * {@code knowledge.#}, elle reçoit tout ce que le contexte annonce, et c'est l'en-tête de
 * type qui désigne le handler dans {@link KnowledgeEventListener}. Le motif tient parce que
 * le contexte est le premier segment de la clé ({@code knowledge.document.uploaded}). Un
 * nouvel événement du contexte arrive ici sans toucher au binding ; il reste à le déclarer
 * dans {@link DomainEventRegistration} et à lui donner son {@code @RabbitHandler}.
 *
 * <p>Elle est déclarée dans les deux rôles, pas seulement dans le worker : Spring AMQP
 * déclare à la première connexion, les déclarations sont idempotentes, et l'API démarrée
 * seule ne doit pas publier dans un exchange sans queue liée — le message serait perdu sans
 * bruit. Durable : elle survit au redémarrage du broker, avec ses messages non consommés.
 */
@Configuration
public class KnowledgeMessagingConfiguration {

    public static final String KNOWLEDGE_EVENTS_QUEUE = "domain.knowledge.events";

    private static final String KNOWLEDGE_EVENTS_PATTERN = "knowledge.#";

    @Bean
    public DomainEventRegistration knowledgeDomainEvents() {
        return new DomainEventRegistration(List.of(DocumentUploaded.class));
    }

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
