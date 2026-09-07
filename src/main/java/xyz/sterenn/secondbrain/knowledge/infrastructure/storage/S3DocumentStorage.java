package xyz.sterenn.secondbrain.knowledge.infrastructure.storage;

import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;

@Component
class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3Client;
    private final String bucket;

    S3DocumentStorage(S3Client s3Client, @Value("${secondbrain.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    // The existence check is not atomic: S3 has no portable equivalent to the CREATE_NEW of a
    // filesystem. It guards against no concurrency — the key is a UUID drawn at save — but
    // against a handler that would call store twice.
    @Override
    public void store(UUID documentId, byte[] content) {
        String key = key(documentId);
        try {
            if (alreadyStored(key)) {
                throw new IllegalStateException("Un original est déjà conservé pour le document " + documentId);
            }
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw unavailable("conservé", e);
        }
    }

    // No catch for absence: DeleteObject returns 204 on a key that does not exist.
    @Override
    public void delete(UUID documentId) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key(documentId))
                    .build());
        } catch (SdkException e) {
            throw unavailable("effacé", e);
        }
    }

    // getObjectAsBytes and definitely not headObject: a HEAD response has no body, and the SDK
    // returns NoSuchKeyException there whether the key or the BUCKET is missing. A misnamed
    // bucket would then return empty for every document, without anything being raised.
    @Override
    public Optional<byte[]> read(UUID documentId) {
        try {
            return Optional.of(s3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key(documentId))
                            .build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw unavailable("relu", e);
        }
    }

    // headObject here, unlike read: the key/bucket ambiguity costs nothing, a missing bucket
    // answers "absent" then makes the putObject fail one line later.
    private boolean alreadyStored(String key) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String key(UUID documentId) {
        return documentId.toString();
    }

    private DocumentStorageUnavailableException unavailable(String pastParticiple, SdkException cause) {
        return new DocumentStorageUnavailableException(
                "Le stockage des originaux n'a pas répondu : l'original de ce document n'a pas pu être "
                        + pastParticiple + ".",
                cause);
    }
}
