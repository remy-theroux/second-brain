package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration.RecordingEmbeddingPort;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * The seven gestures of the mirror, end to end, in the worker role and on real commits. The
 * clock never triggers anything here: the interval is pushed to a day in the test properties and
 * the event is published by hand, exactly as {@code KnowledgeEventListenerTest} does.
 */
@Import({
    TestcontainersConfiguration.class,
    RecordingEmbeddingPortConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
class DriveSynchronisationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * A round that changes nothing leaves nothing to wait for: the assertion is made after the
     * message has had the time to be consumed, and it is the absence of a change that is read.
     */
    private static final Duration LET_THE_ROUND_RUN = Duration.ofSeconds(3);

    private static final String EMAIL = "alice@exemple.fr";

    private static final DriveFolder NOTES = new DriveFolder("a1", "Notes");

    private static final String KEPT_TOKEN = "1789";

    private static final Instant EDITED_LATER = FakeGoogleDrive.MODIFIED_TIME.plus(Duration.ofHours(1));

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FakeGoogleDrive fakeGoogleDrive;

    @Autowired
    private RecordingEmbeddingPort recordingEmbeddingPort;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private WatchedFolderRepository watchedFolderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID alice;
    private UUID connectionId;
    private UUID watchedFolderId;

    @BeforeEach
    void prepare_an_account_whose_drive_has_already_been_read() {
        fakeGoogleDrive.clear();
        recordingEmbeddingPort.clear();
        alice = userRepository
                .save(User.register(new Email(EMAIL), "empreinte"))
                .getId();
        DriveConnection connection =
                DriveConnection.connect(alice, "alice@gmail.com", new RefreshToken("1//jeton"), Instant.now());
        connection.keepChangesPageToken(KEPT_TOKEN);
        connectionId = driveConnectionRepository.save(connection).getId();
        watchedFolderId = watchedFolderRepository
                .save(WatchedFolder.watch(connectionId, NOTES, Instant.now()))
                .getId();
    }

    @AfterEach
    void erase_what_has_been_committed() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
        recordingEmbeddingPort.clear();
    }

    @Test
    void imports_a_file_that_appeared_in_a_watched_folder() {
        fakeGoogleDrive.putFile("a1", "f1", "nouveau.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "a1"));

        requestASynchronisation();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(documents()).singleElement().satisfies(document -> {
                    assertThat(document.getFilename()).isEqualTo("nouveau.md");
                    assertThat(document.getWatchedFolderId()).isEqualTo(watchedFolderId);
                    assertThat(document.getDriveProvenance())
                            .get()
                            .extracting(DriveProvenance::fileId)
                            .isEqualTo("f1");
                }));
    }

    @Test
    void re_ingests_a_document_whose_file_changed_in_the_drive() {
        byte[] newContent = Fixtures.read(Fixtures.STRUCTURED_MD);
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.putFile("a1", "f1", "rapport.md", newContent);
        fakeGoogleDrive.willHaveBeenModifiedAt("f1", EDITED_LATER);
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "a1"));

        requestASynchronisation();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(documents()).singleElement().satisfies(document -> {
                    assertThat(document.getChecksum()).isEqualTo(Checksum.of(newContent));
                    assertThat(document.getDriveProvenance())
                            .get()
                            .extracting(DriveProvenance::modifiedTime)
                            .isEqualTo(EDITED_LATER);
                }));
    }

    @Test
    void removes_a_document_whose_file_left_the_drive() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.willReportChanges(DriveChange.removed("f1"));

        requestASynchronisation();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents()).isEmpty());
    }

    @Test
    void removes_a_document_whose_file_was_sent_to_the_trash() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.willReportChanges(DriveChange.trashed("f1"));

        requestASynchronisation();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents()).isEmpty());
    }

    @Test
    void removes_a_document_whose_file_left_every_watched_folder() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.put(DriveFolder.ROOT, FakeGoogleDrive.folder("z9", "Archives"));
        fakeGoogleDrive.putFile("z9", "f1", "rapport.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "z9"));

        requestASynchronisation();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents()).isEmpty());
    }

    @Test
    void renames_a_document_without_recomputing_its_chunks() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.putFile("a1", "f1", "rapport-2026.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "a1"));

        requestASynchronisation();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents())
                .singleElement()
                .extracting(Document::getFilename)
                .isEqualTo("rapport-2026.md"));
        assertThat(fakeGoogleDrive.handedOverFiles()).isZero();
        assertThat(recordingEmbeddingPort.receivedTexts()).isEmpty();
    }

    @Test
    void keeps_a_document_whose_file_only_moved_inside_a_watched_folder() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.put("a1", FakeGoogleDrive.folder("b1", "2026"));
        fakeGoogleDrive.putFile("b1", "f1", "rapport.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "b1"));

        requestASynchronisation();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(keptPageToken()).contains(fakeGoogleDrive.newStartPageToken()));
        assertThat(documents())
                .singleElement()
                .extracting(Document::getFilename)
                .isEqualTo("rapport.md");
        assertThat(fakeGoogleDrive.handedOverFiles()).isZero();
    }

    /** The costly invariant: an export is rebuilt on every call, so an untouched Doc must not be paid for. */
    @Test
    void does_not_export_again_a_google_document_whose_modified_time_has_not_changed() {
        aDocumentImportedFrom("f1", "Compte rendu.docx", DocumentFormat.DOCX, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.putGoogleDoc("a1", "f1", "Compte rendu", Fixtures.read(Fixtures.HEADINGS_DOCX));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "a1"));

        requestASynchronisation();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(keptPageToken()).contains(fakeGoogleDrive.newStartPageToken()));
        assertThat(fakeGoogleDrive.handedOverFiles()).isZero();
    }

    @Test
    void keeps_the_new_page_token_after_a_complete_round() {
        fakeGoogleDrive.willReportChanges();

        requestASynchronisation();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(keptPageToken()).contains(fakeGoogleDrive.newStartPageToken()));
        assertThat(fakeGoogleDrive.readPageTokens()).containsExactly(KEPT_TOKEN);
    }

    @Test
    void keeps_the_previous_page_token_when_the_round_failed() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.willBeUnavailable();

        requestASynchronisation();
        letTheRoundRun();

        assertThat(keptPageToken()).contains(KEPT_TOKEN);
        assertThat(documents()).hasSize(1);
    }

    /**
     * The gravest failure mode of the mirror: a hiccup read as "this file left every watched
     * folder" would erase the base. Nothing is removed on an incomplete read.
     */
    @Test
    void erases_nothing_when_the_drive_falls_over_while_resolving_the_parents_of_a_file() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.putFile("c9", "f1", "rapport.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "c9"));
        fakeGoogleDrive.willBeUnavailableWhileResolvingParents();

        requestASynchronisation();
        letTheRoundRun();

        assertThat(documents())
                .singleElement()
                .extracting(Document::getFilename)
                .isEqualTo("rapport.md");
        assertThat(keptPageToken()).contains(KEPT_TOKEN);
    }

    /**
     * The same failure mode, and the one the stub reproduced: a climb that stops on a folder
     * Drive hands back no more reads as an empty chain of ancestors. Read as "under no watched
     * folder", it removes the document, its chunks and its original.
     */
    @Test
    void erases_nothing_when_the_parents_of_a_file_cannot_be_climbed() {
        aDocumentImportedFrom("f1", "rapport.md", DocumentFormat.MARKDOWN, FakeGoogleDrive.MODIFIED_TIME);
        fakeGoogleDrive.putFile("c9", "f1", "rapport.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "c9"));

        requestASynchronisation();
        letTheChangePageBeRead();

        assertThat(documents())
                .singleElement()
                .extracting(Document::getFilename)
                .isEqualTo("rapport.md");
        assertThat(keptPageToken()).contains(KEPT_TOKEN);
    }

    /** A token Google no longer knows owes a full scan, never a fresh "from now on". */
    @Test
    void falls_back_on_a_full_scan_when_google_no_longer_knows_the_token() {
        fakeGoogleDrive.putFile("a1", "f1", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willRefuseTheChangeToken();

        requestASynchronisation();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents())
                .extracting(Document::getFilename)
                .containsExactly("structure.md"));
        assertThat(keptPageToken()).contains(fakeGoogleDrive.startPageToken());
    }

    /**
     * A scan that fell over at the three hundredth file of five hundred has left two hundred out
     * of the base, and moving the position forward would keep them out for ever: the change feed
     * only ever reports what moves after the token.
     */
    @Test
    void keeps_no_page_token_when_the_full_scan_it_owed_failed() {
        fakeGoogleDrive.putFile("a1", "f1", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willRefuseTheChangeToken();
        fakeGoogleDrive.willBecomeUnavailableAfter(0);

        requestASynchronisation();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(watchedFolder().getLastImportStatus())
                .isEqualTo(DriveImportStatus.FAILED));
        assertThat(keptPageToken()).isEmpty();
        assertThat(documents()).isEmpty();
    }

    /**
     * The clock is never what triggers the tests, so it is read rather than awaited: the task is
     * registered, and it is a delay between two rounds — a rate would start one while the
     * previous is still going.
     */
    @Test
    void puts_the_rounds_on_a_fixed_delay_clock() {
        assertThat(scheduledTaskHolder.getScheduledTasks())
                .filteredOn(task -> task.getTask().toString().contains("requestASynchronisation"))
                .singleElement()
                .satisfies(task -> assertThat(task.getTask()).isInstanceOf(FixedDelayTask.class));
    }

    private void requestASynchronisation() {
        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.drive-synchronisation.requested",
                new DriveSynchronisationRequested(alice, Instant.now()));
    }

    private void letTheRoundRun() {
        await().pollDelay(LET_THE_ROUND_RUN).atMost(TIMEOUT).until(() -> true);
    }

    /**
     * An absence needs a positive signal to be read at all: without one, a message not yet
     * consumed would let every assertion below pass on an empty round.
     */
    private void letTheChangePageBeRead() {
        await().atMost(TIMEOUT).until(() -> !fakeGoogleDrive.readPageTokens().isEmpty());
        letTheRoundRun();
    }

    private void aDocumentImportedFrom(String fileId, String filename, DocumentFormat format, Instant modifiedTime) {
        byte[] content = Fixtures.read(Fixtures.RAW_TXT);
        documentRepository.save(Document.importedFromDrive(
                alice,
                filename,
                format,
                Checksum.of(content),
                content.length,
                new DriveProvenance(fileId, "https://drive.google.com/file/d/" + fileId + "/view", modifiedTime),
                watchedFolderId));
    }

    private List<Document> documents() {
        return documentRepository.findAllByOwnerId(alice);
    }

    private WatchedFolder watchedFolder() {
        return watchedFolderRepository
                .findByIdAndConnectionId(watchedFolderId, connectionId)
                .orElseThrow();
    }

    private Optional<String> keptPageToken() {
        return driveConnectionRepository.findByOwnerId(alice).orElseThrow().getChangesPageToken();
    }
}
