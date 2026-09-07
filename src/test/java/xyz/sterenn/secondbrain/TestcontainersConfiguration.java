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
 * {@code org.testcontainers.rabbitmq.RabbitMQContainer} and not
 * {@code org.testcontainers.containers.RabbitMQContainer}: Testcontainers 2 moved the class, and
 * Spring Boot 4 only recognises the old one through a deprecated factory.
 *
 * <p>Garage has no {@code @ServiceConnection} — Spring Boot ships none for S3 — and is not
 * optional for all that: the registrar below sets the four defaultless properties that
 * {@code S3ClientConfiguration} and {@code S3DocumentStorage} read.
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
        // pgvector/pgvector and not postgres: the `vector` extension must be shipped by the
        // image for the migration to enable it. Version pinned, like compose.yaml.
        return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:0.8.6-pg17")
                // The image derives from `postgres` but does not bear its name: without this
                // line, Testcontainers refuses to treat it as a PostgreSQL.
                .asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-alpine"));
    }

    @Bean
    GenericContainer<?> garageContainer() {
        // The same docker/garage.toml that compose.yaml mounts: the development stack and the
        // test one cannot drift apart.
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
                // The binary first: the dxflrs/garage image has no ENTRYPOINT.
                .withCommand("/garage", "server", "--single-node", "--default-access-key", "--default-bucket")
                // withCopyFileToContainer and never a bind mount: make check-back drives the host
                // Docker daemon from a container, which does not have the path to mount.
                //
                // /health returns 200 once the --single-node layout is applied, hence once the key
                // and the bucket exist — no race with the first PutObject of a test.
                .waitingFor(Wait.forHttp("/health").forPort(GARAGE_ADMIN_PORT).forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar garageProperties(GenericContainer<?> garageContainer) {
        // As a @Bean, and it is the only way: under Spring Boot 4, injecting a
        // DynamicPropertyRegistry into a @Bean method throws, and @DynamicPropertySource is
        // static per test class, hence has no effect on TestSecondBrainApplication.
        //
        // region and path-style are not set here: they do not depend on the container, and their
        // application.yml defaults are what the context startup checks.
        return registry -> {
            // A supplier: the mapped port only exists once the container has started.
            registry.add(
                    "secondbrain.storage.s3.endpoint",
                    () -> "http://" + garageContainer.getHost() + ":" + garageContainer.getMappedPort(GARAGE_S3_PORT));
            registry.add("secondbrain.storage.s3.bucket", () -> S3_BUCKET);
            registry.add("secondbrain.storage.s3.access-key", () -> S3_ACCESS_KEY);
            registry.add("secondbrain.storage.s3.secret-key", () -> S3_SECRET_KEY);
        };
    }
}
