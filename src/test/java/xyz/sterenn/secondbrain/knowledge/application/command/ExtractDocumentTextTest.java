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
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ExtractDocumentTextTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

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
    void stores_the_extracted_text_and_marks_the_document_extracted() {
        Document document = anUploadedDocument("structure.md", Fixtures.STRUCTURED_MD);

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(text -> assertThat(text.getBlocks())
                        .extracting(TextBlock::getHeading)
                        .contains("Journal de bord"));
        assertThat(reload(document).getStatus()).isEqualTo(DocumentStatus.EXTRACTED);
    }

    @Test
    void a_second_extraction_replaces_the_first_without_duplicating_it() {
        Document document = anUploadedDocument("structure.md", Fixtures.STRUCTURED_MD);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();
    }

    @Test
    void picks_the_extractor_for_the_document_format() {
        Document document = anUploadedDocument("notes.txt", Fixtures.RAW_TXT);

        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                .get()
                .satisfies(text -> assertThat(text.getBlocks()).hasSize(1));
    }

    @Test
    void rejects_a_document_that_does_not_belong_to_the_requester() {
        Document document = anUploadedDocument("notes.txt", Fixtures.RAW_TXT);

        // Last call of the test: the refusal marks the enclosing transaction rollback-only.
        assertThatExceptionOfType(DocumentNotFoundException.class)
                .isThrownBy(() -> commandBus.dispatch(new ExtractDocumentText(document.getId(), UUID.randomUUID())));
    }

    @Test
    void lets_the_refusal_of_an_unusable_document_propagate() {
        Document document = anUploadedDocument("scan.pdf", Fixtures.SCANNED_PDF);

        // Last call of the test, same reason.
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(
                        () -> commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId())));
    }

    private Document anUploadedDocument(String filename, String fixture) {
        UUID owner = userRepository
                .save(User.register(new Email(UUID.randomUUID() + "@exemple.fr"), "empreinte"))
                .getId();
        byte[] content = Fixtures.read(fixture);
        commandBus.dispatch(new UploadDocument(owner, filename, content));
        return documentRepository
                .findByOwnerIdAndChecksum(owner, Checksum.of(content))
                .orElseThrow();
    }

    private Document reload(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }
}
