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

/**
 * Topologie et sérialisation communes à tous les événements métier.
 *
 * <p>Un seul exchange <em>topic</em> : un consommateur futur pourra écouter
 * {@code knowledge.#} sans rien redéclarer côté publication. Chaque consommateur déclare
 * <strong>sa</strong> queue et son binding dans son propre contexte — une queue est une
 * intention de consommation, elle appartient à celui qui consomme.
 *
 * <p>Le convertisseur est celui que Spring Boot donne au {@code RabbitTemplate} et aux
 * listeners : un seul bean {@link MessageConverter}, et les deux côtés parlent la même
 * langue. Les déclarations d'événements sont collectées par {@link ObjectProvider} pour la
 * même raison que les handlers dans {@code BusConfiguration} : le contexte doit démarrer
 * même si aucun contexte borné n'en déclare.
 */
@Configuration
public class AmqpConfiguration {

    public static final String EVENTS_EXCHANGE = "second-brain.events";

    @Bean
    public TopicExchange domainEventsExchange() {
        // durable, non auto-delete : l'exchange survit au redémarrage du broker.
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter domainEventMessageConverter(ObjectProvider<DomainEventRegistration> registrations) {
        List<Class<? extends DomainEvent>> types =
                registrations.stream().flatMap(r -> r.types().stream()).toList();

        // L'en-tête __TypeId__ porte le nom de DomainEventNames dans les deux sens : à
        // l'envoi par la table inversée, à la réception par la table directe.
        //
        // TYPE_ID et non INFERRED (le défaut) : en INFERRED, la réception déduit le type du
        // paramètre du listener et ne consulte jamais l'en-tête — un `on(DocumentUploaded)`
        // désérialiserait n'importe quel corps en DocumentUploaded, et un événement non
        // déclaré partirait à l'envoi sous son nom qualifié de classe, exactement le
        // couplage que ce mapping refuse. Avec TYPE_ID, c'est l'en-tête qui gouverne des
        // deux côtés : un nom absent de la table est confronté aux paquets de confiance du
        // mapper (java.lang, java.util) AVANT tout ClassUtils.forName, qui n'est donc tenté
        // que pour un nom déjà jugé sûr. Ce qui protège, c'est ce filtre — pas l'absence de
        // tentative : « The class 'knowledge.Inconnu' is not in the trusted packages ».
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(DomainEventNames.mappingOf(types));
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.TYPE_ID);

        // Constructeur sans argument : le convertisseur construit son JsonMapper Jackson 3
        // avec le module java.time, et Jackson 3 écrit les Instant en ISO-8601 par défaut.
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
