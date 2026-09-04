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
        // L'adapter n'attrape rien à cet endroit : l'idempotence est celle de DeleteObject,
        // et ce test est ce qui l'établit contre un vrai serveur.
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
