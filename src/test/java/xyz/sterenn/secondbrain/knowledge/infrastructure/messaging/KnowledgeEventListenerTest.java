package xyz.sterenn.secondbrain.knowledge.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.application.command.UploadDocument;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import({TestcontainersConfiguration.class, RecordingEmbeddingPortConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
@ExtendWith(OutputCaptureExtension.class)
class KnowledgeEventListenerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

    @Autowired
    private RecordingEmbeddingPort embeddingPort;

    @Autowired
    private DocumentStorage documentStorage;

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
    void erase_what_has_been_committed() {
        // The cascading foreign key takes the documents and their texts away with the account;
        // the object storage, for its part, takes part in no transaction (ADR-0020).
        createdAccounts.forEach(email -> jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", email));
        createdAccounts.clear();
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
        embeddingPort.clear();
    }

    @Test
    void declares_the_queue_of_the_context() {
        assertThat(amqpAdmin.getQueueInfo("domain.knowledge.events")).isNotNull();
    }

    @Test
    void the_worker_role_starts_without_an_http_server_nor_a_security_filter() {
        assertThat(applicationContext).isNotInstanceOf(WebApplicationContext.class);
        assertThat(applicationContext.getBeanNamesForType(SecurityFilterChain.class))
                .isEmpty();
        assertThat(applicationContext.getBeanNamesForType(KnowledgeEventListener.class))
                .hasSize(1);
    }

    @Test
    void indexes_the_document_whose_upload_is_announced_and_declares_it_ready() {
        Document document = anUploadedDocument("structure.md", Fixtures.STRUCTURED_MD);

        publish(document);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                    .get()
                    .satisfies(text -> assertThat(text.getBlocks()).isNotEmpty());
            assertThat(textChunkRepository.findByDocumentId(document.getId())).isNotEmpty();
            assertThat(statusOf(document)).isEqualTo(DocumentStatus.READY);
        });
    }

    @Test
    void marks_the_document_as_failed_when_the_extraction_refuses() {
        Document document = anUploadedDocument("scan.pdf", Fixtures.SCANNED_PDF);

        publish(document);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(statusOf(document)).isEqualTo(DocumentStatus.FAILED);
            assertThat(errorMessageOf(document)).contains("pas de texte exploitable");
        });
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isEmpty();
    }

    @Test
    void does_not_expose_the_message_of_a_technical_failure() {
        Document document = anUploadedDocument("notes.txt", Fixtures.RAW_TXT);
        documentStorage.delete(document.getId());

        publish(document);

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(errorMessageOf(document)).contains("n'a pas pu être lu"));
    }

    @Test
    void doubles_neither_the_text_nor_the_chunks_when_the_event_is_delivered_twice() {
        Document document = anUploadedDocument("structure.md", Fixtures.STRUCTURED_MD);
        publish(document);
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(statusOf(document)).isEqualTo(DocumentStatus.READY));
        int chunks = textChunkRepository.findByDocumentId(document.getId()).size();

        publish(document);

        await().during(Duration.ofSeconds(2)).atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(statusOf(document)).isEqualTo(DocumentStatus.READY);
            assertThat(textExtractionRepository.findByDocumentId(document.getId()))
                    .isPresent();
            assertThat(textChunkRepository.findByDocumentId(document.getId())).hasSize(chunks);
        });
    }

    @Test
    void marks_the_document_as_failed_when_the_embedding_service_does_not_answer() {
        Document document = anUploadedDocument("structure.md", Fixtures.STRUCTURED_MD);
        embeddingPort.willFail();

        publish(document);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(statusOf(document)).isEqualTo(DocumentStatus.FAILED);
            assertThat(errorMessageOf(document)).contains("vectorisation");
        });
        assertThat(textChunkRepository.findByDocumentId(document.getId())).isEmpty();
        assertThat(textExtractionRepository.findByDocumentId(document.getId())).isPresent();
    }

    @Test
    void rejects_an_undeclared_event_without_reprocessing_it(CapturedOutput output) throws InterruptedException {
        UUID document = UUID.randomUUID();
        Message message = rabbitTemplate
                .getMessageConverter()
                .toMessage(
                        new DocumentUploaded(document, UUID.randomUUID(), Instant.parse("2026-08-25T10:00:00Z")),
                        new MessageProperties());
        message.getMessageProperties().setHeader("__TypeId__", "knowledge.inconnu.survenu");

        // The queue is bound on `knowledge.#`: it receives the whole context, and it is the
        // type header that is judged, not the routing key.
        rabbitTemplate.send("domain.events", "knowledge.inconnu.survenu", message);

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(output).contains("knowledge.inconnu.survenu"));

        int occurrences = occurrencesOf("knowledge.inconnu.survenu", output);
        Thread.sleep(1000);
        assertThat(occurrencesOf("knowledge.inconnu.survenu", output)).isEqualTo(occurrences);
    }

    private void publish(Document document) {
        rabbitTemplate.convertAndSend(
                "domain.events",
                "knowledge.document.uploaded",
                new DocumentUploaded(document.getId(), document.getOwnerId(), Instant.now()));
    }

    private Document anUploadedDocument(String filename, String fixture) {
        String email = UUID.randomUUID() + "@exemple.fr";
        UUID ownerId = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        createdAccounts.add(email);
        byte[] content = Fixtures.read(fixture);
        commandBus.dispatch(new UploadDocument(ownerId, filename, content));
        return documentRepository
                .findByOwnerIdAndChecksum(ownerId, Checksum.of(content))
                .orElseThrow();
    }

    private DocumentStatus statusOf(Document document) {
        return reload(document).getStatus();
    }

    private String errorMessageOf(Document document) {
        return reload(document).getErrorMessage();
    }

    private Document reload(Document document) {
        return documentRepository
                .findByIdAndOwnerId(document.getId(), document.getOwnerId())
                .orElseThrow();
    }

    private int occurrencesOf(String pattern, CapturedOutput output) {
        int total = 0;
        int index = output.getOut().indexOf(pattern);
        while (index >= 0) {
            total++;
            index = output.getOut().indexOf(pattern, index + pattern.length());
        }
        return total;
    }
}
