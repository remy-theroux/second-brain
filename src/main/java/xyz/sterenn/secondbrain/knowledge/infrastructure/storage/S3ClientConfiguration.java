package xyz.sterenn.secondbrain.knowledge.infrastructure.storage;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
class S3ClientConfiguration {

    @Bean
    S3Client s3Client(
            @Value("${secondbrain.storage.s3.endpoint}") String endpoint,
            @Value("${secondbrain.storage.s3.access-key}") String accessKey,
            @Value("${secondbrain.storage.s3.secret-key}") String secretKey,
            @Value("${secondbrain.storage.s3.region}") String region,
            @Value("${secondbrain.storage.s3.path-style}") boolean pathStyle) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyle)
                // Nommé plutôt que découvert par ServiceLoader : c'est le seul client HTTP du
                // classpath, et l'écrire ici fait échouer la compilation plutôt que le premier
                // dépôt le jour où build.gradle.kts cesserait de le déclarer.
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // Bornent un stockage qui accepte le TCP puis ne répond plus : sans elles, les
                // reprises du SDK tiennent près de deux minutes, et donc deux minutes de
                // connexion PostgreSQL, `store` étant appelé après le saveAndFlush.
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofSeconds(90)))
                // Garage v2.3.0 refuse le CRC32 en remorque que le SDK envoie depuis la 2.30, par
                // un « Bad request: Invalid payload signature » qui ne nomme ni l'un ni l'autre.
                // Sans encodage par blocs le checksum repart en en-tête simple, donc conservé.
                .serviceConfiguration(configuration -> configuration.chunkedEncodingEnabled(false))
                .build();
    }
}
