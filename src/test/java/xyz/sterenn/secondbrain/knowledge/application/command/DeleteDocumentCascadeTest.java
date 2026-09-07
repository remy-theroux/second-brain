package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextChunk;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * No {@code @Transactional}: inside a transaction, Hibernate would return the
 * {@code TextExtraction} from its first-level cache without querying the database, and the test
 * would pass whatever the migration says. Hence the cleanup in {@code @AfterEach}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeleteDocumentCascadeTest {

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private final List<String> createdAccounts = new ArrayList<>();

    @AfterEach
    void erases_what_was_committed() {
        createdAccounts.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        createdAccounts.clear();
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void deleting_a_document_takes_its_extracted_text_with_it() {
        Document document = anUploadedDocument();
        commandBus.dispatch(new ExtractDocumentText(document.getId(), document.getOwnerId()));
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM knowledge_text_blocks", Integer.class))
                .isZero();
    }

    @Test
    void deleting_a_document_takes_its_chunks_with_it() {
        Document document = anUploadedDocument();
        textChunkRepository.saveAll(List.of(TextChunk.of(
                document.getId(), 0, new Chunk("Titre", "Un corps."), KnowledgeFixture.aVector(0.5f), Instant.now())));
        assertThat(textChunkRepository.findByDocumentId(document.getId())).isNotEmpty();

        commandBus.dispatch(new DeleteDocument(document.getId(), document.getOwnerId()));

        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
    }

    private Document anUploadedDocument() {
        String email = UUID.randomUUID() + "@exemple.fr";
        UUID owner = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        createdAccounts.add(email);
        byte[] content = Fixtures.read(Fixtures.RAW_TXT);
        commandBus.dispatch(new UploadDocument(owner, "notes.txt", content));
        return documentRepository
                .findByOwnerIdAndChecksum(owner, Checksum.of(content))
                .orElseThrow();
    }
}
