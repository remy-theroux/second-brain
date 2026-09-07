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
                // Named rather than discovered by ServiceLoader: it is the only HTTP client on
                // the classpath, and writing it here breaks the compilation rather than the first
                // upload the day build.gradle.kts stops declaring it.
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // These bound a storage that accepts TCP then stops answering: without them the
                // SDK retries last nearly two minutes, hence two minutes of PostgreSQL
                // connection, `store` being called after the saveAndFlush.
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofSeconds(90)))
                // Garage v2.3.0 rejects the trailing CRC32 the SDK sends since 2.30, with a
                // "Bad request: Invalid payload signature" that names neither of them. Without
                // chunked encoding the checksum goes back as a plain header, so it is kept.
                .serviceConfiguration(configuration -> configuration.chunkedEncodingEnabled(false))
                .build();
    }
}
