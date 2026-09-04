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

    // Le contrôle d'existence n'est pas atomique : S3 n'a pas d'équivalent portable au
    // CREATE_NEW du disque. Il ne défend contre aucune concurrence — la clé est un UUID tiré
    // au save — mais contre un handler qui appellerait store deux fois.
    @Override
    public void store(UUID documentId, byte[] content) {
        String cle = cle(documentId);
        try {
            if (dejaConserve(cle)) {
                throw new IllegalStateException("Un original est déjà conservé pour le document " + documentId);
            }
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(cle).build(), RequestBody.fromBytes(content));
        } catch (SdkException e) {
            throw indisponible("conservé", e);
        }
    }

    // Aucun catch d'absence : DeleteObject rend 204 sur une clé qui n'existe pas.
    @Override
    public void delete(UUID documentId) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(cle(documentId))
                    .build());
        } catch (SdkException e) {
            throw indisponible("effacé", e);
        }
    }

    // getObjectAsBytes et surtout pas headObject : une réponse à un HEAD n'a pas de corps, et
    // le SDK y rend NoSuchKeyException que ce soit la clé ou le BUCKET qui manque. Un bucket mal
    // nommé rendrait alors empty pour tous les documents, sans que rien ne soit levé.
    @Override
    public Optional<byte[]> read(UUID documentId) {
        try {
            return Optional.of(s3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(cle(documentId))
                            .build())
                    .asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (SdkException e) {
            throw indisponible("relu", e);
        }
    }

    // headObject ici, à l'inverse de read : l'ambiguïté clé/bucket ne coûte rien, un bucket
    // absent fait répondre « absent » puis échouer le putObject une ligne plus loin.
    private boolean dejaConserve(String cle) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(cle).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private String cle(UUID documentId) {
        return documentId.toString();
    }

    private DocumentStorageUnavailableException indisponible(String participe, SdkException cause) {
        return new DocumentStorageUnavailableException(
                "Le stockage des originaux n'a pas répondu : l'original de ce document n'a pas pu être " + participe
                        + ".",
                cause);
    }
}
