package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.ImportPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveFolderImportRequested;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveContentUnreachableException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DuplicateDriveContentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * The worker role, as {@code KnowledgeEventListenerTest} sees it: real commits, cleaned in
 * {@code @AfterEach}, and the import travels through the broker rather than through a direct
 * call — a type absent from the registration would be rejected, and the walk would never start.
 */
@Import({
    TestcontainersConfiguration.class,
    RecordingEmbeddingPortConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
class DriveFolderImportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String EMAIL = "alice@exemple.fr";

    private static final DriveFolder NOTES = new DriveFolder("a1", "Notes");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private FakeGoogleDrive fakeGoogleDrive;

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
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID alice;
    private UUID connectionId;
    private UUID watchedFolderId;

    @BeforeEach
    void prepare_an_account_watching_a_folder() {
        fakeGoogleDrive.clear();
        alice = userRepository
                .save(User.register(new Email(EMAIL), "empreinte"))
                .getId();
        connectionId = driveConnectionRepository
                .save(DriveConnection.connect(alice, "alice@gmail.com", new RefreshToken("1//jeton"), Instant.now()))
                .getId();
        watchedFolderId = watchedFolderRepository
                .save(WatchedFolder.watch(connectionId, NOTES, Instant.now()))
                .getId();
    }

    @AfterEach
    void erase_what_has_been_committed() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void imports_every_supported_file_of_a_watched_folder() {
        fakeGoogleDrive.putFile("a1", "f1", "signets.pdf", Fixtures.read(Fixtures.BOOKMARKS_PDF));
        fakeGoogleDrive.putFile("a1", "f2", "titres.docx", Fixtures.read(Fixtures.HEADINGS_DOCX));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents())
                .extracting(Document::getFilename)
                .containsExactlyInAnyOrder("signets.pdf", "titres.docx"));
        assertThat(documents()).allSatisfy(document -> {
            assertThat(document.getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
            assertThat(document.getDriveProvenance()).isPresent();
            assertThat(document.getWatchedFolderId()).isEqualTo(watchedFolderId);
        });
        assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED);
    }

    @Test
    void imports_the_files_of_a_subfolder_too() {
        fakeGoogleDrive.put("a1", FakeGoogleDrive.folder("b1", "2026"));
        fakeGoogleDrive.putFile("b1", "f1", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents())
                .extracting(Document::getFilename)
                .containsExactly("structure.md"));
    }

    @Test
    void leaves_an_unsupported_file_out_without_failing_the_import() {
        fakeGoogleDrive.putFile("a1", "f1", "photo.jpg", "des octets d'image".getBytes());
        fakeGoogleDrive.putFile("a1", "f2", "signets.pdf", Fixtures.read(Fixtures.BOOKMARKS_PDF));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("signets.pdf");
        // An image is not a rejection: a Drive is full of them, and listing them would drown the
        // files their owner can actually do something about.
        assertThat(reloadTheFolder().getRejections()).isEmpty();
    }

    @Test
    void keeps_the_documents_already_imported_when_the_drive_becomes_unreachable() {
        fakeGoogleDrive.putFile("a1", "f1", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.putFile("a1", "f2", "brut.txt", Fixtures.read(Fixtures.RAW_TXT));
        fakeGoogleDrive.willBecomeUnavailableAfter(1);

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.FAILED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("structure.md");
        assertThat(reloadTheFolder().getLastImportError()).contains("Google Drive");
    }

    @Test
    void asks_for_a_reconnection_when_the_authorization_is_withdrawn_during_the_import() {
        fakeGoogleDrive.putFile("a1", "f1", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportARevokedAuthorization();

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.FAILED));
        assertThat(reloadTheConnection().getStatus()).isEqualTo(DriveConnectionStatus.NEEDS_RECONNECTION);
        assertThat(reloadTheFolder().getLastImportError()).isEqualTo(DriveAuthorizationRevokedException.MESSAGE);
    }

    @Test
    void leaves_out_a_file_that_vanished_between_the_listing_and_its_download() {
        fakeGoogleDrive.putFile("a1", "f1", "disparu.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.putFile("a1", "f2", "brut.txt", Fixtures.read(Fixtures.RAW_TXT));
        fakeGoogleDrive.willVanishAtDownload("f1");

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("brut.txt");
        assertThat(reloadTheFolder().getRejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.getFilename()).isEqualTo("disparu.md");
            assertThat(rejection.getReason()).isEqualTo(DriveContentUnreachableException.MESSAGE);
        });
    }

    @Test
    void records_a_content_a_previous_file_of_the_same_import_already_brought() {
        fakeGoogleDrive.putFile("a1", "f1", "rapport.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.putFile("a1", "f2", "rapport (1).md", Fixtures.read(Fixtures.STRUCTURED_MD));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("rapport.md");
        assertThat(reloadTheFolder().getRejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.getFilename()).isEqualTo("rapport (1).md");
            assertThat(rejection.getReason()).isEqualTo(DuplicateDriveContentException.MESSAGE);
        });
    }

    @Test
    void records_the_reason_of_a_file_left_out() {
        fakeGoogleDrive.putOversizedFile("a1", "f1", "archive.pdf", ImportPolicy.MAX_FILE_SIZE + 1);
        fakeGoogleDrive.putFile("a1", "f2", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("structure.md");
        assertThat(reloadTheFolder().getRejections()).singleElement().satisfies(rejection -> {
            assertThat(rejection.getFilename()).isEqualTo("archive.pdf");
            assertThat(rejection.getDriveFileId()).isEqualTo("f1");
            assertThat(rejection.getReason()).contains("20 Mo");
        });
    }

    private void requestTheImport() {
        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.drive-folder-import.requested",
                new DriveFolderImportRequested(watchedFolderId, alice, Instant.now()));
    }

    private List<Document> documents() {
        return documentRepository.findAllByOwnerId(alice);
    }

    private DriveImportStatus outcome() {
        return reloadTheFolder().getLastImportStatus();
    }

    private DriveConnection reloadTheConnection() {
        return driveConnectionRepository.findByOwnerId(alice).orElseThrow();
    }

    private WatchedFolder reloadTheFolder() {
        return watchedFolderRepository
                .findByIdAndConnectionId(watchedFolderId, connectionId)
                .orElseThrow();
    }
}
