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

    private static final byte[] CONTENT = "le contenu d'origine".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private DocumentStorage documentStorage;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    @AfterEach
    void emptyTheOriginals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void stores_then_reads_back_a_content() {
        UUID document = UUID.randomUUID();

        documentStorage.store(document, CONTENT);

        assertThat(documentStorage.read(document))
                .hasValueSatisfying(reloaded -> assertThat(reloaded).isEqualTo(CONTENT));
    }

    @Test
    void returns_nothing_for_a_document_without_an_original() {
        assertThat(documentStorage.read(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deletes_a_stored_content() {
        UUID document = UUID.randomUUID();
        documentStorage.store(document, CONTENT);

        documentStorage.delete(document);

        assertThat(documentStorage.read(document)).isEmpty();
    }

    @Test
    void stays_silent_when_deleting_what_does_not_exist() {
        // The adapter catches nothing here: the idempotence is DeleteObject's own, and this test
        // is what establishes it against a real server.
        documentStorage.delete(UUID.randomUUID());
    }

    @Test
    void refuses_to_overwrite_an_already_stored_original() {
        UUID document = UUID.randomUUID();
        documentStorage.store(document, CONTENT);

        assertThatThrownBy(() -> documentStorage.store(document, "autre chose".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalStateException.class);
    }
}
