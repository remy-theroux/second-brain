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
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentStorageUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;

/**
 * Adapter stockage objet du port {@link DocumentStorage}. Un objet par document, dans un
 * bucket unique. Le nom d'origine du fichier ne sert qu'à l'affichage et vit en base : rien
 * de ce que l'utilisateur a saisi n'entre dans une clé.
 *
 * <p><strong>La clé est {@code documentId.toString()} nu, sans préfixe.</strong> Un
 * {@code originals/} répéterait le nom du bucket, qui dit déjà ce qu'il contient, et
 * n'ouvrirait aucun regroupement utile : les clés sont des UUID v4, sans ordre ni parenté,
 * donc un préfixe ne découperait rien qu'un {@code ListObjectsV2} puisse exploiter. Il ne se
 * justifierait qu'à partager un bucket entre plusieurs familles d'artefacts — ce qui n'est
 * pas le cas, et ce jour-là ce sera un second bucket plutôt qu'une convention posée
 * d'avance.
 *
 * <p>Toute {@link SdkException} devient une {@link DocumentStorageUnavailableException} :
 * aucune exception du SDK n'atteint l'application ni le domaine, qui ne savent pas qu'un
 * SDK existe. Le message du SDK reste dans la cause, où il va au journal.
 *
 * <p><strong>Un stockage objet ne participe à aucune transaction</strong> — exactement comme
 * le système de fichiers qu'il remplace. Ce que cette classe écrit survit à un rollback
 * survenu après elle. <strong>C'est l'invariant d'ADR-0020 qui tient ici, pas son
 * énoncé</strong> : cet ADR s'intitule « Le système de fichiers ne participe à aucune
 * transaction » et son corps parle de disque, de fichier et de répertoire des originaux —
 * tout cela a changé de support. Ce qu'il décide, en revanche, vaut à l'identique : la ligne
 * d'abord, l'original ensuite, et les deux fuites assumées dans les deux sens. C'est à ce
 * titre qu'il fait toujours autorité sur cette classe. L'aléa, lui, s'est déplacé — ce n'est
 * plus une {@code IOException} sur un disque local mais un aller-retour réseau, donc la fuite
 * qu'ADR-0020 assume entre l'effacement de la ligne et celui de l'original devient plus
 * probable.
 */
@Component
class S3DocumentStorage implements DocumentStorage {

    private final S3Client s3Client;
    private final String bucket;

    S3DocumentStorage(S3Client s3Client, @Value("${secondbrain.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    /**
     * <strong>Le contrôle d'existence n'est pas atomique, et c'est assumé.</strong> Le
     * {@code CREATE_NEW} qu'il remplace l'était, parce que le noyau tranchait ; S3 n'a pas
     * d'équivalent portable — {@code If-None-Match} sur {@code PutObject} n'est pas documenté
     * comme implémenté par Garage, et un adapter ne se bâtit pas sur une fonction que le
     * serveur n'a pas promise.
     *
     * <p>La perte est nulle en pratique : la fenêtre de course exige deux {@code store}
     * concurrents portant le <em>même</em> identifiant, or c'est un UUID tiré au {@code save}
     * — jamais fourni par l'appelant, jamais dérivé du contenu, jamais rejoué. Ce contrôle ne
     * défend donc pas contre une concurrence, il rend visible un défaut de câblage (un
     * handler qui appellerait {@code store} deux fois), et contre celui-là deux appels
     * séquentiels suffisent.
     *
     * <p>{@link IllegalStateException} et non une exception métier : une clé déjà prise n'est
     * pas un refus opposé à l'utilisateur, c'est le signe d'un défaut ailleurs dans le code.
     */
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

    /**
     * Aucun {@code catch} d'absence : {@code DeleteObject} rend {@code 204} sur une clé qui
     * n'existe pas. L'idempotence promise par le port est donc celle de S3, pas une
     * précaution de cette classe — et c'est
     * {@code S3DocumentStorageTest.reste_silencieux_en_effacant_ce_qui_n_existe_pas} qui la
     * vérifie contre le vrai serveur, là où un {@code catch} ici n'aurait fait que la
     * simuler.
     */
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

    /**
     * {@code getObjectAsBytes}, et <strong>surtout pas {@code headObject}</strong>. C'est le
     * point subtil de cette classe : une réponse à un {@code HEAD} n'a pas de corps, le SDK
     * n'y lit donc que le statut, et un {@code 404} y devient {@link NoSuchKeyException} que
     * ce soit la <em>clé</em> ou le <em>bucket</em> qui manque. Ici la confusion serait
     * grave : un bucket mal nommé ferait rendre {@link Optional#empty()} pour tous les
     * documents — « original perdu » pour chacun — sans que rien ne soit levé nulle part. Un
     * {@code GET} porte un corps XML : contre Garage, un bucket absent y rend bien
     * {@link NoSuchBucketException} (« Bucket not found ») là où une clé absente rend
     * {@link NoSuchKeyException} (« Key not found »), et les deux sont {@code final}, sœurs
     * sous {@code S3Exception} — aucune n'hérite de l'autre. La première tombe donc dans le
     * {@code catch} qui lève, la seconde dans celui qui rend {@code empty}.
     */
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

    /**
     * {@code headObject} ici, à l'inverse de {@link #read(UUID)}, et pour deux raisons qui se
     * tiennent : on ne veut pas rapatrier tout l'objet pour apprendre qu'il existe, et
     * l'ambiguïté « clé absente ou bucket absent » ne coûte rien à cet endroit précis. Un
     * bucket manquant fait répondre « absent », donc on enchaîne sur le {@code putObject},
     * qui échoue — le défaut se voit une ligne plus loin, et {@code store} n'aboutit pas en
     * silence sur un bucket qui n'existe pas.
     *
     * <p>Et il se voit <em>sous son vrai nom</em> : contre Garage, un {@code PutObject} vers
     * un bucket absent rend bien {@link NoSuchBucketException} « Bucket not found », que le
     * {@code catch} de {@code store} traduit en français avec la cause au journal. Constaté,
     * pas supposé — et la nuance vaut d'être gardée, parce que ce n'est vrai que du corps
     * envoyé d'un bloc : tant que l'envoi partait en {@code aws-chunked}, le même appel
     * échouait en {@code SdkClientException} « Error writing request body to server », le
     * serveur répondant {@code 404} pendant que {@code HttpURLConnection} poussait encore le
     * corps. C'est {@code S3ClientConfiguration} qui a fermé ce chemin-là.
     */
    private boolean dejaConserve(String cle) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(cle).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    /**
     * L'identifiant est un UUID produit à l'enregistrement du document, jamais une chaîne
     * venue de la requête : aucune clé ne peut être forgée par l'utilisateur. La conversion
     * explicite en {@code String} le rappelle plutôt qu'elle ne le corrige.
     */
    private String cle(UUID documentId) {
        return documentId.toString();
    }

    /**
     * Le message dit ce qui a échoué, <strong>pas sur quel identifiant</strong> : c'est une
     * phrase affichable telle quelle, comme toutes celles du package d'exceptions, et un UUID
     * au milieu la ferait lire comme une ligne de journal. L'identifiant n'est pas perdu pour
     * autant — la cause le porte, et le consommateur d'événements le journalise avec le
     * document qu'il traitait.
     */
    private DocumentStorageUnavailableException indisponible(String participe, SdkException cause) {
        return new DocumentStorageUnavailableException(
                "Le stockage des originaux n'a pas répondu : l'original de ce document n'a pas pu être " + participe
                        + ".",
                cause);
    }
}
