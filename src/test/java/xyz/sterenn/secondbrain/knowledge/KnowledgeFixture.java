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

    /** {@code DeleteObjects} caps at 1000 keys per call: beyond that, S3 rejects the request. */
    private static final int DELETION_BATCH = 1000;

    private KnowledgeFixture() {}

    public static String token(AccessTokenIssuer accessTokenIssuer, UUID account) {
        Instant now = Instant.now();
        return accessTokenIssuer
                .issue(account, now, now.plus(Duration.ofHours(1)))
                .value();
    }

    /**
     * {@code @Transactional} rolls back the database, never the object storage: without this
     * cleanup an original survives and the adapter's refusal to overwrite fails a neighbouring
     * scenario.
     */
    public static void emptyTheOriginals(S3Client s3Client, String bucket) {
        List<ObjectIdentifier> keys =
                s3Client
                        .listObjectsV2Paginator(
                                ListObjectsV2Request.builder().bucket(bucket).build())
                        .contents()
                        .stream()
                        .map(object ->
                                ObjectIdentifier.builder().key(object.key()).build())
                        .toList();

        for (int start = 0; start < keys.size(); start += DELETION_BATCH) {
            List<ObjectIdentifier> batch = keys.subList(start, Math.min(start + DELETION_BATCH, keys.size()));
            DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(batch).build())
                    .build());
            // A 200 is not success here: DeleteObjects reports failures key by key in the response
            // body, so the SDK throws nothing.
            if (!response.errors().isEmpty()) {
                String failures = response.errors().stream()
                        .map(error -> error.key() + " (" + error.code() + " : " + error.message() + ")")
                        .collect(Collectors.joining(", "));
                throw new IllegalStateException(
                        "Le nettoyage du bucket " + bucket + " a laissé des originaux derrière lui : " + failures);
            }
        }
    }

    public static Embedding aVector(float value) {
        float[] values = new float[EmbeddingPolicy.DIMENSIONS];
        Arrays.fill(values, value);
        return Embedding.of(values);
    }

    /**
     * The reference question of the proximity tests: oriented along dimension 0 only.
     * {@link #aVector} does not fit — it returns vectors that are all collinear, hence at zero
     * cosine distance from one another.
     */
    public static Embedding aQuestion() {
        float[] values = new float[EmbeddingPolicy.DIMENSIONS];
        values[0] = 1f;
        return Embedding.of(values);
    }

    /** The closer {@code proximity} gets to 1, the closer to {@link #aQuestion()}. */
    public static Embedding aNearbyVector(float proximity) {
        float[] values = new float[EmbeddingPolicy.DIMENSIONS];
        values[0] = proximity;
        values[1] = 1f - proximity;
        return Embedding.of(values);
    }
}
