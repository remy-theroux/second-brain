package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.event.DocumentUploaded;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;

/**
 * No {@code @Transactional}: the announcement only leaves at commit, a test transaction would keep
 * it from leaving. The account and the document are therefore really written, and erased in
 * {@code @AfterEach} — the cascade takes the document with the account, the bucket is emptied apart.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
class UploadDocumentAnnouncementTest {

    private static final String EMAIL = "gaston@exemple.fr";
    private static final String OBSERVATION = "test.observation.depot";
    private static final byte[] CONTENT = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID account;

    @BeforeEach
    void prepares_an_account_and_an_observation_queue() {
        recordingNotificationSender.clear();
        account = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, "chevalpile42");
        amqpAdmin.declareQueue(new Queue(OBSERVATION));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION,
                Binding.DestinationType.QUEUE,
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.document.uploaded",
                null));
    }

    @AfterEach
    void erases_what_was_committed() {
        amqpAdmin.deleteQueue(OBSERVATION);
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void announces_the_uploaded_document_once_the_upload_is_committed() {
        commandBus.dispatch(new UploadDocument(account, "rapport.txt", CONTENT));

        Object received = rabbitTemplate.receiveAndConvert(
                OBSERVATION, Duration.ofSeconds(5).toMillis());

        Document document = documentRepository
                .findByOwnerIdAndChecksum(account, Checksum.of(CONTENT))
                .orElseThrow();
        assertThat(received).isInstanceOf(DocumentUploaded.class);
        DocumentUploaded event = (DocumentUploaded) received;
        assertThat(event.documentId()).isEqualTo(document.getId());
        assertThat(event.ownerId()).isEqualTo(account);
        assertThat(event.occurredAt()).isNotNull();
    }
}
