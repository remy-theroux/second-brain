package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
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
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveFolderImportRequested;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextChunkRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.TextExtractionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveImportStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.GoogleWorkspaceType;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/** The worker role, as {@code DriveFolderImportTest} sees it: real commits, cleaned in {@code @AfterEach}. */
@Import({
    TestcontainersConfiguration.class,
    RecordingEmbeddingPortConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
class GoogleDocImportTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** Long enough to hold a second import out of the window a first one is still finishing in. */
    private static final Duration SETTLING = Duration.ofSeconds(3);

    private static final String EMAIL = "doc@exemple.fr";

    private static final DriveFolder NOTES = new DriveFolder("a1", "Notes");

    /** Past the floor of fifty characters a document has to clear to be exploitable: see ADR-0025. */
    private static final String BODY =
            "Le corps du document exporté, assez long pour compter dans le plancher de caractères.";

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
    private TextExtractionRepository textExtractionRepository;

    @Autowired
    private TextChunkRepository textChunkRepository;

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
    void imports_a_google_document_of_a_watched_folder() {
        fakeGoogleDrive.putGoogleDoc("a1", "g1", "Compte rendu du 3 mars", Fixtures.read(Fixtures.HEADINGS_DOCX));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(documents())
                .extracting(Document::getFilename)
                .containsExactly("Compte rendu du 3 mars.docx"));
        assertThat(onlyDocument().getFormat()).isEqualTo(DocumentFormat.DOCX);
        assertThat(onlyDocument().getSource()).isEqualTo(DocumentSource.GOOGLE_DRIVE);
        assertThat(onlyDocument().getDriveProvenance())
                .map(DriveProvenance::fileId)
                .contains("g1");
        assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED);
    }

    /** The whole reason the export is a DOCX and not a PDF: its headings are told, not guessed. */
    @Test
    void keeps_the_headings_of_the_google_document() {
        fakeGoogleDrive.putGoogleDoc("a1", "g1", "Compte rendu du 3 mars", Fixtures.read(Fixtures.HEADINGS_DOCX));

        requestTheImport();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.READY));
        assertThat(headings()).contains("Rapport annuel", "Première partie", "Seconde partie");
    }

    @Test
    void leaves_a_spreadsheet_out_without_failing_the_import() {
        fakeGoogleDrive.putWorkspaceFile("a1", "s1", "Budget 2026", GoogleWorkspaceType.SPREADSHEET);
        fakeGoogleDrive.putGoogleDoc("a1", "g1", "Compte rendu", Fixtures.read(Fixtures.HEADINGS_DOCX));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("Compte rendu.docx");
        // A sheet is not a rejection: it would want its own typology, like an image would.
        assertThat(reloadTheFolder().getRejections()).isEmpty();
    }

    @Test
    void leaves_a_presentation_out() {
        fakeGoogleDrive.putWorkspaceFile("a1", "p1", "Soutenance", GoogleWorkspaceType.PRESENTATION);
        fakeGoogleDrive.putGoogleDoc("a1", "g1", "Compte rendu", Fixtures.read(Fixtures.HEADINGS_DOCX));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("Compte rendu.docx");
        assertThat(reloadTheFolder().getRejections()).isEmpty();
    }

    @Test
    void reports_a_document_too_large_to_export_without_failing_the_import() {
        fakeGoogleDrive.putGoogleDocTooLargeToExport("a1", "g1", "Thèse");
        fakeGoogleDrive.putFile("a1", "f2", "structure.md", Fixtures.read(Fixtures.STRUCTURED_MD));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(outcome()).isEqualTo(DriveImportStatus.SUCCEEDED));
        assertThat(documents()).extracting(Document::getFilename).containsExactly("structure.md");
        assertThat(reloadTheFolder().getRejections()).singleElement().satisfies(rejection -> {
            // The name of the file in the Drive of its owner, not the one made up for downstream.
            assertThat(rejection.getFilename()).isEqualTo("Thèse");
            assertThat(rejection.getDriveFileId()).isEqualTo("g1");
            assertThat(rejection.getReason()).contains("10 Mo");
        });
    }

    @Test
    void marks_a_document_without_usable_text_as_failed() {
        fakeGoogleDrive.putGoogleDoc("a1", "g1", "Brouillon", docx("Brouillon", "Trop court."));

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.FAILED);
            assertThat(onlyDocument().getErrorMessage()).contains("pas de texte exploitable");
        });
    }

    @Test
    void re_imports_the_latest_version_of_a_document_modified_since() {
        fakeGoogleDrive.putGoogleDoc(
                "a1", "g1", "Compte rendu", Fixtures.read(Fixtures.HEADINGS_DOCX), docx("Compte rendu révisé", BODY));
        requestTheImport();
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.READY));

        fakeGoogleDrive.willHaveBeenModifiedAt("g1", FakeGoogleDrive.MODIFIED_TIME.plus(Duration.ofHours(1)));
        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.READY);
            assertThat(headings()).contains("Compte rendu révisé");
        });
        assertThat(documents()).hasSize(1);
    }

    /**
     * The export is not deterministic — the archive Google builds embeds metadata and timestamps —
     * so a Doc compared by checksum would be re-ingested at every single import.
     */
    @Test
    void does_not_re_ingest_a_google_document_whose_modified_time_has_not_changed() {
        fakeGoogleDrive.putGoogleDoc(
                "a1", "g1", "Compte rendu", Fixtures.read(Fixtures.HEADINGS_DOCX), docx("Un autre export", BODY));
        requestTheImport();
        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.READY));
        Checksum checksum = onlyDocument().getChecksum();
        int chunks =
                textChunkRepository.findByDocumentId(onlyDocument().getId()).size();
        Instant firstImport = reloadTheFolder().getLastImportAt();

        requestTheImport();

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(reloadTheFolder().getLastImportAt())
                .isNotEqualTo(firstImport));
        await().during(SETTLING).atMost(TIMEOUT).untilAsserted(() -> {
            assertThat(onlyDocument().getChecksum()).isEqualTo(checksum);
            assertThat(onlyDocument().getStatus()).isEqualTo(DocumentStatus.READY);
            assertThat(textChunkRepository.findByDocumentId(onlyDocument().getId()))
                    .hasSize(chunks);
        });
    }

    private void requestTheImport() {
        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.drive-folder-import.requested",
                new DriveFolderImportRequested(watchedFolderId, alice, Instant.now()));
    }

    /** A DOCX built here rather than in the versioned fixtures: each exists for one assertion. */
    private static byte[] docx(String heading, String body) {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.setStyle("Heading1");
            title.createRun().setText(heading);
            document.createParagraph().createRun().setText(body);
            document.write(bytes);
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    private List<String> headings() {
        return textExtractionRepository.findByDocumentId(onlyDocument().getId()).orElseThrow().getBlocks().stream()
                .map(TextBlock::getHeading)
                .toList();
    }

    private List<Document> documents() {
        return documentRepository.findAllByOwnerId(alice);
    }

    private Document onlyDocument() {
        assertThat(documents()).hasSize(1);
        return documents().getFirst();
    }

    private DriveImportStatus outcome() {
        return reloadTheFolder().getLastImportStatus();
    }

    private WatchedFolder reloadTheFolder() {
        return watchedFolderRepository
                .findByIdAndConnectionId(watchedFolderId, connectionId)
                .orElseThrow();
    }
}
