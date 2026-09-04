package xyz.sterenn.secondbrain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

public final class KnowledgeFixture {

    /** {@code DeleteObjects} plafonne à 1000 clés par appel : au-delà, S3 refuse la requête. */
    private static final int LOT_DE_SUPPRESSION = 1000;

    private KnowledgeFixture() {}

    public static String jeton(AccessTokenIssuer accessTokenIssuer, UUID compte) {
        Instant maintenant = Instant.now();
        return accessTokenIssuer
                .issue(compte, maintenant, maintenant.plus(Duration.ofHours(1)))
                .value();
    }

    /**
     * {@code @Transactional} annule la base, jamais le stockage objet : sans ce nettoyage, un
     * original survit et le refus d'écrasement de l'adapter fait échouer un scénario voisin.
     */
    public static void videLesOriginaux(S3Client s3Client, String bucket) {
        List<ObjectIdentifier> cles =
                s3Client
                        .listObjectsV2Paginator(
                                ListObjectsV2Request.builder().bucket(bucket).build())
                        .contents()
                        .stream()
                        .map(objet ->
                                ObjectIdentifier.builder().key(objet.key()).build())
                        .toList();

        for (int debut = 0; debut < cles.size(); debut += LOT_DE_SUPPRESSION) {
            List<ObjectIdentifier> lot = cles.subList(debut, Math.min(debut + LOT_DE_SUPPRESSION, cles.size()));
            DeleteObjectsResponse reponse = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(lot).build())
                    .build());
            // Un 200 ne vaut pas succès ici : DeleteObjects range les échecs clé par clé dans le
            // corps de la réponse, et le SDK ne lève donc rien.
            if (!reponse.errors().isEmpty()) {
                String echecs = reponse.errors().stream()
                        .map(erreur -> erreur.key() + " (" + erreur.code() + " : " + erreur.message() + ")")
                        .collect(Collectors.joining(", "));
                throw new IllegalStateException(
                        "Le nettoyage du bucket " + bucket + " a laissé des originaux derrière lui : " + echecs);
            }
        }
    }

    public static Embedding unVecteur(float valeur) {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        Arrays.fill(valeurs, valeur);
        return Embedding.of(valeurs);
    }

    /**
     * La question de référence des tests de proximité : orientée sur la seule dimension 0.
     * {@link #unVecteur} ne convient pas — elle rend des vecteurs tous colinéaires, donc à
     * distance cosinus nulle deux à deux.
     */
    public static Embedding uneQuestion() {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        valeurs[0] = 1f;
        return Embedding.of(valeurs);
    }

    /** D'autant plus proche de {@link #uneQuestion()} que {@code proximite} approche de 1. */
    public static Embedding unVecteurProche(float proximite) {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        valeurs[0] = proximite;
        valeurs[1] = 1f - proximite;
        return Embedding.of(valeurs);
    }
}
