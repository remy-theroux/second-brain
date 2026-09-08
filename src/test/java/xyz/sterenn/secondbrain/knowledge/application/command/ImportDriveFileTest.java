package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDriveContentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;

/**
 * No {@code @Transactional}: an announcement only leaves at commit, and what this scenario is
 * about is precisely which imports announce and which stay silent.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
class ImportDriveFileTest {

    private static final String EMAIL = "alice@exemple.fr";

    private static final UUID NOTES = UUID.randomUUID();

    private static final String OBSERVATION = "test.observation.import-drive";

    private static final Instant MODIFIED_TIME = Instant.parse("2026-09-08T10:15:30Z");

    private static final byte[] REPORT = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

    /** Nothing is expected on the queue: long enough for an announcement to arrive, short enough to pay. */
    private static final Duration SILENCE = Duration.ofSeconds(1);

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentStorage documentStorage;

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

    private UUID alice;

    @BeforeEach
    void prepare_an_account_and_an_observation_queue() {
        recordingNotificationSender.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, "chevalpile42");
        amqpAdmin.declareQueue(new Queue(OBSERVATION));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION,
                Binding.DestinationType.QUEUE,
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.document.uploaded",
                null));
    }

    @AfterEach
    void erase_what_was_committed() {
        amqpAdmin.deleteQueue(OBSERVATION);
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void imports_a_file_that_the_base_does_not_hold() {
        commandBus.dispatch(new ImportDriveFile(alice, NOTES, aDriveFile("f1", "rapport.txt", REPORT), REPORT));

        Document document = onlyDocument();
        assertThat(document.getFilename()).isEqualTo("rapport.txt");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
        assertThat(document.getDriveProvenance())
                .contains(new DriveProvenance("f1", "https://drive.google.com/file/d/f1/view", MODIFIED_TIME));
        assertThat(documentStorage.read(document.getId())).contains(REPORT);
        assertThat(announcement()).isEqualTo(document.getId());
    }

    @Test
    void attaches_the_drive_source_to_a_document_already_uploaded_by_hand() {
        commandBus.dispatch(new UploadDocument(alice, "rapport.txt", REPORT));
        UUID uploaded = onlyDocument().getId();
        assertThat(announcement()).isEqualTo(uploaded);

        commandBus.dispatch(new ImportDriveFile(alice, NOTES, aDriveFile("f1", "rapport.txt", REPORT), REPORT));

        Document document = onlyDocument();
        assertThat(document.getId()).isEqualTo(uploaded);
        assertThat(document.getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
        assertThat(document.getDriveProvenance()).map(DriveProvenance::fileId).contains("f1");
        // Nothing was re-run: the content has not moved, so the pipeline has nothing to redo.
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(nothingAnnounced()).isTrue();
    }

    @Test
    void does_nothing_on_a_second_import_of_the_same_drive_file() {
        DriveFile file = aDriveFile("f1", "rapport.txt", REPORT);
        commandBus.dispatch(new ImportDriveFile(alice, NOTES, file, REPORT));
        UUID imported = onlyDocument().getId();
        assertThat(announcement()).isEqualTo(imported);

        commandBus.dispatch(new ImportDriveFile(alice, NOTES, file, REPORT));

        assertThat(onlyDocument().getId()).isEqualTo(imported);
        assertThat(nothingAnnounced()).isTrue();
    }

    /** A folder unwatched then watched again is a new row: without this, its count reads zero for ever. */
    @Test
    void hands_an_already_imported_document_over_to_the_folder_watched_now() {
        DriveFile file = aDriveFile("f1", "rapport.txt", REPORT);
        commandBus.dispatch(new ImportDriveFile(alice, NOTES, file, REPORT));
        assertThat(announcement()).isNotNull();
        UUID notesWatchedAgain = UUID.randomUUID();

        commandBus.dispatch(new ImportDriveFile(alice, notesWatchedAgain, file, REPORT));

        assertThat(onlyDocument().getWatchedFolderId()).isEqualTo(notesWatchedAgain);
        assertThat(nothingAnnounced()).isTrue();
    }

    @Test
    void re_imports_nothing_when_only_the_name_changed() {
        commandBus.dispatch(new ImportDriveFile(alice, NOTES, aDriveFile("f1", "rapport.txt", REPORT), REPORT));
        assertThat(announcement()).isNotNull();

        commandBus.dispatch(new ImportDriveFile(alice, NOTES, aDriveFile("f1", "rapport-2026.txt", REPORT), REPORT));

        assertThat(onlyDocument().getFilename()).isEqualTo("rapport.txt");
        assertThat(nothingAnnounced()).isTrue();
    }

    @Test
    void refuses_a_content_a_document_already_carries_from_another_drive_file() {
        commandBus.dispatch(new ImportDriveFile(alice, NOTES, aDriveFile("f1", "rapport.txt", REPORT), REPORT));
        assertThat(announcement()).isNotNull();

        assertThatExceptionOfType(DuplicateDriveContentException.class)
                .isThrownBy(() -> commandBus.dispatch(
                        new ImportDriveFile(alice, NOTES, aDriveFile("f2", "copie.txt", REPORT), REPORT)));

        assertThat(onlyDocument().getDriveProvenance())
                .map(DriveProvenance::fileId)
                .contains("f1");
        assertThat(nothingAnnounced()).isTrue();
    }

    private static DriveFile aDriveFile(String fileId, String filename, byte[] content) {
        return new DriveFile(
                fileId,
                filename,
                DocumentFormat.fromFilename(filename),
                content.length,
                "https://drive.google.com/file/d/" + fileId + "/view",
                MODIFIED_TIME);
    }

    private Document onlyDocument() {
        List<Document> documents = documentRepository.findAllByOwnerId(alice);
        assertThat(documents).hasSize(1);
        return documents.getFirst();
    }

    private UUID announcement() {
        Object received = rabbitTemplate.receiveAndConvert(
                OBSERVATION, Duration.ofSeconds(5).toMillis());
        assertThat(received).isInstanceOf(DocumentUploaded.class);
        return ((DocumentUploaded) received).documentId();
    }

    private boolean nothingAnnounced() {
        return rabbitTemplate.receiveAndConvert(OBSERVATION, SILENCE.toMillis()) == null;
    }
}
