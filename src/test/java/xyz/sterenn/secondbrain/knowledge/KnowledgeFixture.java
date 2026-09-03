package xyz.sterenn.secondbrain.knowledge;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import xyz.sterenn.secondbrain.knowledge.domain.EmbeddingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Embedding;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

public final class KnowledgeFixture {

    /** {@code DeleteObjects} plafonne à 1000 clés par appel : au-delà, S3 refuse la requête. */
    private static final int LOT_DE_SUPPRESSION = 1000;

    private KnowledgeFixture() {
        // classe utilitaire
    }

    public static String jeton(AccessTokenIssuer accessTokenIssuer, UUID compte) {
        Instant maintenant = Instant.now();
        return accessTokenIssuer
                .issue(compte, maintenant, maintenant.plus(Duration.ofHours(1)))
                .value();
    }

    /**
     * Vide le bucket des originaux écrits par un test.
     *
     * <p>{@code @Transactional} annule la base, <strong>jamais le stockage objet</strong> :
     * sans ce nettoyage, un test laisserait derrière lui des objets que plus aucune ligne ne
     * désigne, et le refus d'écrasement de l'adapter finirait par faire échouer un scénario
     * voisin.
     *
     * <p>Le risque a tout de même <em>baissé</em> en changeant de support : {@code
     * build/test-originals} survivait d'une exécution à l'autre, alors que le conteneur
     * Garage est jeté à la fin. La fuite ne traverse donc plus qu'une seule exécution — dont
     * toutes les classes partagent le contexte Spring, donc le conteneur.
     *
     * <p>Les clés sont relevées d'abord et effacées ensuite. Itérer un paginateur en
     * effaçant ce qu'il vient de rendre fonctionne sur S3, mais rien ici ne justifie de
     * reposer sur cette garantie-là.
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
            s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(lot).build())
                    .build());
        }
    }

    public static Embedding unVecteur(float valeur) {
        float[] valeurs = new float[EmbeddingPolicy.DIMENSIONS];
        Arrays.fill(valeurs, valeur);
        return Embedding.of(valeurs);
    }
}
