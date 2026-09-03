package xyz.sterenn.secondbrain.knowledge.infrastructure.storage;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Le client S3 dont dépend {@link S3DocumentStorage}.
 *
 * <p>Ici, et non dans {@code config/} : ce package-là porte ce qui est transverse — le
 * décodeur JWT y vit <em>parce que</em> le filtre de sécurité global s'en sert. Un
 * {@code S3Client} ne sert qu'un adapter d'un seul contexte borné, il reste chez lui.
 *
 * <p>Le nom, lui, évite une collision : {@code software.amazon.awssdk.services.s3.S3Configuration}
 * existe, et une classe nommée {@code S3Configuration} piégerait le premier import
 * automatique venu.
 *
 * <p>Un {@code @Bean} plutôt qu'un client construit dans le constructeur de l'adapter, pour
 * deux raisons : les tests ont besoin d'injecter <em>le même</em> client pour vider le
 * bucket entre deux scénarios, et {@code S3Client} est {@code AutoCloseable} — Spring
 * appelle {@code close()} à l'arrêt, sans qu'aucune ligne d'ici ne s'en charge.
 */
@Configuration
class S3ClientConfiguration {

    /**
     * {@code @Value} sur les paramètres, motif du projet, et pas de {@code @ConfigurationProperties} :
     * il n'en existe aucune dans {@code src/main}, et une classe de plus n'achèterait ni
     * validation (elle est déjà dans le builder du SDK) ni relaxed binding. Le bénéfice
     * décisif est ailleurs : un placeholder <strong>sans défaut</strong> fait échouer le
     * démarrage, exactement comme {@code secondbrain.jwt.secret}.
     *
     * <p>C'est le cas des quatre premières propriétés. Un défaut y viserait silencieusement
     * un bucket qui n'est pas le bon, et le premier dépôt écrirait ailleurs qu'où on croit :
     * une panne muette, découverte à la première relecture d'un original.
     *
     * <p>{@link StaticCredentialsProvider} et non la chaîne par défaut du SDK : celle-ci
     * interrogerait les variables {@code AWS_*}, puis {@code ~/.aws/credentials}, puis le
     * service de métadonnées de l'instance, avec un délai à chaque étape. Rien de tout cela
     * ne s'applique — les identifiants viennent de notre configuration, ou l'application ne
     * démarre pas.
     *
     * <p>{@link UrlConnectionHttpClient} nommé explicitement plutôt que découvert par
     * {@code ServiceLoader} : c'est le seul client HTTP du classpath (build.gradle.kts
     * exclut les deux autres), et l'écrire ici fait échouer la <em>compilation</em> le jour
     * où on le retirerait, au lieu de faire échouer le premier dépôt de document.
     *
     * <p><strong>{@code chunkedEncodingEnabled(false)} est obligatoire face à Garage</strong>,
     * et le raisonnement mérite d'être gardé parce que le symptôme n'y renvoie pas. Depuis
     * la version 2.30, le SDK calcule un CRC32 sur toute requête qui l'accepte et l'envoie
     * <em>en remorque</em> : le corps part en {@code Content-Encoding: aws-chunked} avec
     * {@code x-amz-content-sha256: STREAMING-AWS4-HMAC-SHA256-PAYLOAD-TRAILER} et un
     * {@code x-amz-trailer: x-amz-checksum-crc32}. Garage v2.3.0 refuse cette combinaison —
     * « Bad request: Invalid payload signature », un {@code 400} qui ne nomme ni le checksum
     * ni la remorque. Les en-têtes ci-dessus ont été relevés sur le fil, par un
     * {@code ExecutionInterceptor} posé sur trois configurations successives.
     *
     * <p>Deux réglages font passer la requête, et ils ne coûtent pas la même chose.
     * {@code requestChecksumCalculation(WHEN_REQUIRED)} garde l'encodage par blocs et
     * <strong>supprime le CRC32</strong> : plus de remorque, mais plus de contrôle
     * d'intégrité non plus. Celui-ci, lui, supprime l'encodage par blocs et
     * <strong>garde le CRC32</strong>, qui repart en simple en-tête
     * {@code x-amz-checksum-crc32} — le corps est alors signé par son vrai SHA-256 plutôt
     * que par un jeton de flux. C'est donc le moins désarmant des deux, et il tombe juste :
     * le contenu déposé transite entièrement en mémoire (ADR-0021), on connaît sa longueur,
     * il n'y a aucun flux à découper. AWS S3 natif accepte cette forme, elle ne ferme rien.
     */
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
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .serviceConfiguration(configuration -> configuration.chunkedEncodingEnabled(false))
                .build();
    }
}
