package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DocumentNotFoundException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MarkDocumentProcessingFailedTest {

    private static final String REASON = "Ce document ne contient pas de texte exploitable.";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    @AfterEach
    void cleansTheOriginals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void marks_the_document_failed_with_its_reason() {
        Document document = anUploadedDocument();

        commandBus.dispatch(new MarkDocumentProcessingFailed(document.getId(), document.getOwnerId(), REASON));

        assertThat(documentRepository
                        .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                        .orElseThrow())
                .satisfies(reloaded -> {
                    assertThat(reloaded.getStatus()).isEqualTo(DocumentStatus.FAILED);
                    assertThat(reloaded.getErrorMessage()).isEqualTo(REASON);
                });
    }

    @Test
    void refuses_to_mark_a_document_that_has_disappeared() {
        // Last call of the test: the refusal marks the enclosing transaction rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(
                        new MarkDocumentProcessingFailed(UUID.randomUUID(), UUID.randomUUID(), REASON)));
    }

    private Document anUploadedDocument() {
        UUID owner = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] content = Fixtures.read(Fixtures.RAW_TXT);
        commandBus.dispatch(new UploadDocument(owner, "notes.txt", content));
        return documentRepository
                .findByOwnerIdAndChecksum(owner, Checksum.of(content))
                .orElseThrow();
    }
}
