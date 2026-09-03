package xyz.sterenn.secondbrain.knowledge.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;

/**
 * L'adapter ne peut plus se tester sans Spring : il ne dépend plus d'un chemin mais d'un
 * serveur, et le seul moyen honnête de savoir ce que S3 répond est de le demander à un vrai
 * serveur S3 — d'où {@code @SpringBootTest} et le conteneur Garage. Cette classe injecte le
 * <strong>port</strong> {@link DocumentStorage}, pas l'adapter : c'est le contrat du domaine
 * qu'elle vérifie, l'adapter n'étant que ce qui se trouve derrière aujourd'hui.
 *
 * <p>Le {@code S3Client} et le nom du bucket ne servent qu'au nettoyage : rien dans les
 * scénarios ne va regarder le stockage autrement que par le port.
 *
 * <p>Un scénario de l'ancienne version a disparu et ce n'est pas un oubli :
 * {@code cree_le_repertoire_de_destination_s_il_manque} n'a plus d'objet — un stockage objet
 * n'a pas de répertoire, et le bucket est une précondition du déploiement, pas quelque chose
 * que l'adapter fabrique à la volée.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class S3DocumentStorageTest {

    private static final byte[] CONTENU = "le contenu d'origine".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private DocumentStorage documentStorage;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String bucketDesOriginaux;

    @AfterEach
    void videLesOriginaux() {
        KnowledgeFixture.videLesOriginaux(s3Client, bucketDesOriginaux);
    }

    @Test
    void conserve_puis_relit_un_contenu() {
        UUID document = UUID.randomUUID();

        documentStorage.store(document, CONTENU);

        assertThat(documentStorage.read(document))
                .hasValueSatisfying(relu -> assertThat(relu).isEqualTo(CONTENU));
    }

    @Test
    void ne_rend_rien_pour_un_document_sans_original() {
        assertThat(documentStorage.read(UUID.randomUUID())).isEmpty();
    }

    @Test
    void efface_un_contenu_conserve() {
        UUID document = UUID.randomUUID();
        documentStorage.store(document, CONTENU);

        documentStorage.delete(document);

        assertThat(documentStorage.read(document)).isEmpty();
    }

    @Test
    void reste_silencieux_en_effacant_ce_qui_n_existe_pas() {
        // C'est ce test, et lui seul, qui établit que DeleteObject rend 204 sur une clé
        // absente. L'adapter n'attrape rien à cet endroit : l'idempotence vient du serveur,
        // et la vérifier ici contre le vrai Garage vaut mieux qu'un catch qui la simulerait
        // sans jamais dire si S3 la promet.
        documentStorage.delete(UUID.randomUUID());
    }

    @Test
    void refuse_d_ecraser_un_original_deja_conserve() {
        UUID document = UUID.randomUUID();
        documentStorage.store(document, CONTENU);

        assertThatThrownBy(() -> documentStorage.store(document, "autre chose".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
    }
}
