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
     * <p>C'est le cas de trois des cinq propriétés lues ici : {@code endpoint},
     * {@code access-key} et {@code secret-key}. Un défaut y viserait silencieusement un autre
     * serveur, ou s'y présenterait sous d'autres identifiants, et le premier dépôt écrirait
     * ailleurs qu'où on croit : une panne muette, découverte à la première relecture d'un
     * original. {@code region} et {@code path-style}, elles, <em>ont</em> un défaut, et
     * application.yml dit pourquoi chacune peut se le permettre. Quant au bucket — le
     * quatrième placeholder sans défaut — il ne se lit pas ici : c'est {@link
     * S3DocumentStorage} qui le lit, parce que c'est lui qui le nomme dans chaque requête.
     *
     * <p>{@link StaticCredentialsProvider} et non la chaîne par défaut du SDK : celle-ci
     * interrogerait les variables {@code AWS_*}, puis {@code ~/.aws/credentials}, puis le
     * service de métadonnées de l'instance, avec un délai à chaque étape. Rien de tout cela
     * ne s'applique — les identifiants viennent de notre configuration, ou l'application ne
     * démarre pas.
     *
     * <p>{@link UrlConnectionHttpClient} nommé explicitement plutôt que découvert par
     * {@code ServiceLoader} : c'est le seul client HTTP qui atteint le classpath. Cinq se
     * présentent, et aucun ne passe tout seul — {@code url-connection-client},
     * {@code aws-crt-client} et {@code apache-client} sont déclarés en scope {@code test} par
     * le pom de {@code s3}, donc sans effet ici ; {@code apache5-client} et
     * {@code netty-nio-client} arrivent en scope {@code runtime} par le pom parent
     * {@code services} et sont exclus dans build.gradle.kts. Celui-ci n'atteint donc le
     * classpath que parce que build.gradle.kts le déclare à part — l'écrire ici fait échouer
     * la <em>compilation</em> le jour où on retirerait cette déclaration, au lieu de faire
     * échouer le premier dépôt de document.
     *
     * <p><strong>Les deux timeouts bornent un service figé, pas un transfert normal.</strong>
     * Un objet pèse au plus 20 Mo ({@code spring.servlet.multipart.max-file-size}) et part
     * vers un Garage du même réseau : il passe en une fraction de seconde, et aucune de ces
     * deux valeurs ne le concerne. Elles ne mordent que sur un stockage qui accepte le TCP
     * puis ne répond plus. Sans elles, ce cas-là dure : le client HTTP n'oppose que ses
     * timeouts de socket, 30 s en lecture comme en écriture
     * ({@code SdkHttpConfigurationOption}), et le mode de reprise par défaut —
     * {@code LEGACY}, dont le maximum est de quatre tentatives — les remet en jeu à chaque
     * fois, soit près de deux minutes, pauses de backoff en plus. Or {@code UploadDocumentHandler} appelle {@code store}
     * <em>après</em> le {@code saveAndFlush} : ces deux minutes sont deux minutes de connexion
     * PostgreSQL tenue par la transaction du bus. <strong>C'est exactement le calcul écrit
     * dans application.yml</strong> pour les timeouts de Jakarta Mail — dix inscriptions
     * bloquées figent un pool Hikari de dix connexions — puis repris pour
     * {@code spring.rabbitmq.connection-timeout}. Même panne, troisième parade.
     *
     * <p>{@code apiCallAttemptTimeout} à 30 s aligne la tentative sur le timeout de lecture du
     * client HTTP : il ne raccourcit rien de ce qui aboutissait, il rend la borne explicite.
     * {@code apiCallTimeout} à 90 s est celui qui compte, parce qu'il borne l'appel
     * <em>entier</em>, reprises et pauses comprises. Sans lui la durée maximale n'est pas
     * choisie, elle se déduit — du nombre de tentatives du mode de reprise, que
     * {@code RetryMode.Resolver} lit dans {@code AWS_RETRY_MODE} et dans le profil AWS, donc
     * hors de cette configuration. Avec lui, elle est à nous.
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
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .apiCallTimeout(Duration.ofSeconds(90)))
                .serviceConfiguration(configuration -> configuration.chunkedEncodingEnabled(false))
                .build();
    }
}
