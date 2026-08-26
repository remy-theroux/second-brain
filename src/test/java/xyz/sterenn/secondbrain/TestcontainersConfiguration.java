package xyz.sterenn.secondbrain;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Fournit une PostgreSQL et un RabbitMQ jetables pour les tests. {@code @ServiceConnection}
 * auto-configure la datasource et la connexion AMQP vers ces conteneurs.
 *
 * <p>RabbitMQ est là pour toute la suite, pas seulement pour les tests du socle
 * d'événements : Spring AMQP ne se connecte qu'au premier envoi, mais un dépôt de document
 * publie, et un conteneur de plus partagé coûte moins qu'une configuration de test à part.
 *
 * <p>{@code org.testcontainers.rabbitmq.RabbitMQContainer} et non
 * {@code org.testcontainers.containers.RabbitMQContainer} : Testcontainers 2 a déplacé la
 * classe, et Spring Boot 4 ne reconnaît l'ancienne que par une fabrique dépréciée.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        // Sans console de gestion : un test n'en a pas l'usage, et l'image est plus légère.
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    }
}
