package xyz.sterenn.secondbrain;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * {@code org.testcontainers.rabbitmq.RabbitMQContainer} et non
 * {@code org.testcontainers.containers.RabbitMQContainer} : Testcontainers 2 a déplacé la classe,
 * et Spring Boot 4 ne reconnaît l'ancienne que par une fabrique dépréciée.
 *
 * <p>Garage n'a pas de {@code @ServiceConnection} — Spring Boot n'en fournit aucun pour S3 —
 * et n'est donc pas optionnel pour autant : le registrar ci-dessous pose les quatre propriétés
 * sans défaut que lisent {@code S3ClientConfiguration} et {@code S3DocumentStorage}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int GARAGE_S3_PORT = 3900;
    private static final int GARAGE_ADMIN_PORT = 3903;
    private static final String S3_BUCKET = "second-brain-originals";
    private static final String S3_ACCESS_KEY = "second-brain-test";
    private static final String S3_SECRET_KEY = "secret-de-test-second-brain";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        // pgvector/pgvector et non postgres : l'extension `vector` doit être fournie par
        // l'image pour que la migration puisse l'activer. Version épinglée, comme compose.yaml.
        return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:0.8.6-pg17")
                // L'image dérive de `postgres` mais ne porte pas son nom : sans cette ligne,
                // Testcontainers refuse de la traiter comme une PostgreSQL.
                .asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    }

    @Bean
    GenericContainer<?> garageContainer() {
        // Le même docker/garage.toml que monte compose.yaml : la pile de développement et
        // celle des tests ne peuvent pas dériver.
        Path garageConfig = Path.of("docker", "garage.toml").toAbsolutePath();
        if (!Files.exists(garageConfig)) {
            throw new IllegalStateException("docker/garage.toml introuvable au chemin " + garageConfig
                    + " : la JVM de test ne tourne pas depuis la racine du projet.");
        }
        return new GenericContainer<>(DockerImageName.parse("dxflrs/garage:v2.3.0"))
                .withExposedPorts(GARAGE_S3_PORT, GARAGE_ADMIN_PORT)
                .withCopyFileToContainer(MountableFile.forHostPath(garageConfig), "/etc/garage.toml")
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", S3_ACCESS_KEY)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", S3_SECRET_KEY)
                .withEnv("GARAGE_DEFAULT_BUCKET", S3_BUCKET)
                // Le binaire d'abord : l'image dxflrs/garage n'a pas d'ENTRYPOINT.
                .withCommand("/garage", "server", "--single-node", "--default-access-key", "--default-bucket")
                // withCopyFileToContainer et jamais un bind mount : make check-back pilote le démon
                // Docker de l'hôte depuis un conteneur, qui n'a pas le chemin à monter.
                //
                // /health rend 200 quand le layout de --single-node est appliqué, donc quand la clé
                // et le bucket existent — pas de course avec le premier PutObject d'un test.
                .waitingFor(Wait.forHttp("/health").forPort(GARAGE_ADMIN_PORT).forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar garageProperties(GenericContainer<?> garageContainer) {
        // En @Bean et c'est la seule voie : sous Spring Boot 4, injecter un
        // DynamicPropertyRegistry dans une méthode @Bean lève, et @DynamicPropertySource est
        // statique par classe de test, donc sans effet sur TestSecondBrainApplication.
        //
        // region et path-style ne sont pas posées ici : elles ne dépendent pas du conteneur, et
        // leurs défauts d'application.yml sont ce que le démarrage du contexte vérifie.
        return registry -> {
            // Un supplier : le port mappé n'existe qu'une fois le conteneur démarré.
            registry.add(
                    "secondbrain.storage.s3.endpoint",
                    () -> "http://" + garageContainer.getHost() + ":" + garageContainer.getMappedPort(GARAGE_S3_PORT));
            registry.add("secondbrain.storage.s3.bucket", () -> S3_BUCKET);
            registry.add("secondbrain.storage.s3.access-key", () -> S3_ACCESS_KEY);
            registry.add("secondbrain.storage.s3.secret-key", () -> S3_SECRET_KEY);
        };
    }
}
