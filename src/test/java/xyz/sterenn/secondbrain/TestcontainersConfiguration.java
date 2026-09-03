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
 * Fournit une PostgreSQL, un RabbitMQ et un Garage jetables pour les tests.
 * {@code @ServiceConnection} auto-configure la datasource et la connexion AMQP vers les deux
 * premiers.
 *
 * <p>RabbitMQ est là pour toute la suite, pas seulement pour les tests du socle
 * d'événements : Spring AMQP ne se connecte qu'au premier envoi, mais un dépôt de document
 * publie, et un conteneur de plus partagé coûte moins qu'une configuration de test à part.
 *
 * <p>{@code org.testcontainers.rabbitmq.RabbitMQContainer} et non
 * {@code org.testcontainers.containers.RabbitMQContainer} : Testcontainers 2 a déplacé la
 * classe, et Spring Boot 4 ne reconnaît l'ancienne que par une fabrique dépréciée.
 *
 * <p>Garage, lui, n'a **pas** de {@code @ServiceConnection} : Spring Boot n'en fournit aucun
 * pour S3, et il n'y aurait de toute façon rien à auto-configurer — les propriétés qu'il
 * alimente (`secondbrain.storage.s3.*`) sont les nôtres, pas celles d'un starter connu. C'est
 * le registrar ci-dessous qui fait le raccordement, à la main.
 *
 * <p>Il n'est donc pas optionnel : aucune des quatre propriétés que pose le registrar n'a de
 * valeur par défaut, et deux beans se les partagent au démarrage —
 * {@code S3ClientConfiguration} lit {@code endpoint}, {@code access-key} et
 * {@code secret-key}, {@code S3DocumentStorage} lit {@code bucket}. Un Garage qui ne démarre
 * pas, c'est un contexte Spring qui ne démarre pas — pour toute la suite, pas seulement pour
 * les tests du stockage.
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
        // Le même docker/garage.toml que monte compose.yaml : une seule source, donc la pile
        // de développement et celle des tests ne peuvent pas dériver.
        Path garageConfig = Path.of("docker", "garage.toml").toAbsolutePath();
        if (!Files.exists(garageConfig)) {
            // MountableFile.forHostPath dépend du répertoire de travail de la JVM de test —
            // Gradle le fixe au répertoire du projet, et l'IDE délègue à Gradle ici — mais si
            // ce n'est pas le cas, l'échec serait un NoSuchFileException indéchiffrable au
            // démarrage du conteneur plutôt que ce message explicite.
            throw new IllegalStateException("docker/garage.toml introuvable au chemin " + garageConfig
                    + " : la JVM de test ne tourne pas depuis la racine du projet.");
        }
        return new GenericContainer<>(DockerImageName.parse("dxflrs/garage:v2.3.0"))
                .withExposedPorts(GARAGE_S3_PORT, GARAGE_ADMIN_PORT)
                .withCopyFileToContainer(MountableFile.forHostPath(garageConfig), "/etc/garage.toml")
                .withEnv("GARAGE_DEFAULT_ACCESS_KEY", S3_ACCESS_KEY)
                .withEnv("GARAGE_DEFAULT_SECRET_KEY", S3_SECRET_KEY)
                .withEnv("GARAGE_DEFAULT_BUCKET", S3_BUCKET)
                // Le binaire d'abord : l'image dxflrs/garage n'a pas d'ENTRYPOINT, donc la
                // commande doit le nommer. withCommand("server", ...) échoue faute de binaire
                // implicite.
                .withCommand("/garage", "server", "--single-node", "--default-access-key", "--default-bucket")
                // withCopyFileToContainer et jamais un bind mount : make check-back lance
                // Gradle dans un conteneur qui pilote le démon Docker de l'hôte, donc
                // Testcontainers y démarre des conteneurs frères. Un withFileSystemBind
                // demanderait à l'hôte de monter un chemin qui n'existe que dans le conteneur
                // Gradle, et échouerait. withCopyFileToContainer lit le fichier avec cette JVM
                // et l'envoie en tar par l'API Docker : il traverse ce montage sans rien savoir
                // de lui.
                //
                // /health sur le port d'administration plutôt qu'un message de journal : c'est
                // le seul endpoint non authentifié de l'API d'administration, il rend 200 quand
                // le quorum est atteint — donc quand le layout de --single-node est appliqué.
                // L'amorçage de Garage (layout, clé, bucket) s'exécute avant le bind de ses
                // serveurs HTTP : quand /health répond, le bucket existe déjà, sans course
                // possible avec le premier PutObject d'un test.
                .waitingFor(Wait.forHttp("/health").forPort(GARAGE_ADMIN_PORT).forStatusCode(200));
    }

    @Bean
    DynamicPropertyRegistrar garageProperties(GenericContainer<?> garageContainer) {
        // DynamicPropertyRegistrar en @Bean, et c'est la seule voie. Injecter un
        // DynamicPropertyRegistry dans une méthode @Bean lève une exception sous Spring Boot 4 :
        // spring.testcontainers.dynamic-property-registry-injection vaut fail par défaut. Et
        // @DynamicPropertySource est une méthode statique par classe de test — il faudrait la
        // recopier dans chacune, et elle ne s'appliquerait pas à TestSecondBrainApplication, qui
        // n'est pas un test. Le registrar, lui, est traité par une auto-configuration
        // (TestcontainersPropertySourceAutoConfiguration), donc des deux côtés.
        //
        // Quatre propriétés seulement : region et path-style ne sont PAS posées ici, et c'est
        // délibéré. Elles ne dépendent pas du conteneur — la région doit valoir le s3_region
        // de docker/garage.toml, que Testcontainers et Compose montent tous les deux, et
        // l'adressage par chemin vaut partout. Les reposer ici en ferait une seconde source
        // qui pourrait dériver de ce fichier-là ; leurs défauts d'application.yml suffisent,
        // et c'est le démarrage du contexte qui vérifie qu'ils suffisent.
        //
        // Les trois constantes (bucket, clé, secret) sont partagées entre ce registrar et
        // garageContainer() ci-dessus, pour qu'ils ne puissent pas diverger.
        return registry -> {
            // Un supplier, jamais une valeur calculée à la construction : le port mappé
            // n'existe qu'une fois le conteneur démarré.
            registry.add(
                    "secondbrain.storage.s3.endpoint",
                    () -> "http://" + garageContainer.getHost() + ":" + garageContainer.getMappedPort(GARAGE_S3_PORT));
            registry.add("secondbrain.storage.s3.bucket", () -> S3_BUCKET);
            registry.add("secondbrain.storage.s3.access-key", () -> S3_ACCESS_KEY);
            registry.add("secondbrain.storage.s3.secret-key", () -> S3_SECRET_KEY);
        };
    }
}
