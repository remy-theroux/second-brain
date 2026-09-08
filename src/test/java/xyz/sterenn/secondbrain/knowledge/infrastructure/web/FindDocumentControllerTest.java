package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.application.command.ExtractDocumentText;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FindDocumentControllerTest {

    private static final String PASSWORD = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID alice;
    private String aliceToken;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    @AfterEach
    void erase_the_originals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    @Test
    void returns_the_document_and_the_text_extracted_from_it() throws Exception {
        Document document = upload(aliceToken, alice, "structure.md", Fixtures.STRUCTURED_MD);
        commandBus.dispatch(new ExtractDocumentText(document.getId(), alice));

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filename").value("structure.md"))
                .andExpect(jsonPath("$.format").value("MARKDOWN"))
                .andExpect(jsonPath("$.type").value("TEXTUAL"))
                .andExpect(jsonPath("$.status").value("EXTRACTED"))
                .andExpect(jsonPath("$.extraction.extractedAt").isNotEmpty())
                .andExpect(jsonPath("$.extraction.characterCount").isNumber())
                .andExpect(jsonPath("$.extraction.blocks[*].heading", hasItem("Journal de bord")))
                .andExpect(jsonPath("$.extraction.blocks[0].text").isNotEmpty());
    }

    @Test
    void announces_the_type_without_an_extraction_as_long_as_the_processing_has_not_happened() throws Exception {
        Document document = upload(aliceToken, alice, "notes.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.type").value("TEXTUAL"))
                .andExpect(jsonPath("$.extraction").doesNotExist())
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void returns_the_reason_of_a_failed_document_and_no_extraction() throws Exception {
        Document document = upload(aliceToken, alice, "scan.txt", Fixtures.RAW_TXT);
        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage").value("Ce document ne contient pas de texte exploitable."))
                .andExpect(jsonPath("$.extraction").doesNotExist());
    }

    @Test
    void announces_a_document_uploaded_by_hand_without_a_drive_link() throws Exception {
        Document document = upload(aliceToken, alice, "notes.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.driveLink").doesNotExist());
    }

    @Test
    void announces_the_drive_origin_and_the_link_of_an_imported_document() throws Exception {
        byte[] content = "contenu importé".getBytes(StandardCharsets.UTF_8);
        Document document = documentRepository.save(Document.importedFromDrive(
                alice,
                "notes.md",
                DocumentFormat.MARKDOWN,
                Checksum.of(content),
                content.length,
                new DriveProvenance(
                        "f1", "https://drive.google.com/file/d/f1/view", Instant.parse("2026-09-08T10:15:30Z")),
                UUID.randomUUID()));

        mockMvc.perform(get("/api/documents/" + document.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("GOOGLE_DRIVE"))
                .andExpect(jsonPath("$.driveLink").value("https://drive.google.com/file/d/f1/view"));
    }

    @Test
    void makes_an_unknown_id_not_found() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void makes_the_document_of_another_account_not_found() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        Document bobsDocument =
                upload(KnowledgeFixture.token(accessTokenIssuer, bob), bob, "chez-bob.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + bobsDocument.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuses_the_read_without_a_token() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    private Document upload(String token, UUID ownerId, String filename, String fixture) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file", filename, "application/octet-stream", Fixtures.read(fixture)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(ownerId).stream()
                .filter(document -> document.getFilename().equals(filename))
                .findFirst()
                .orElseThrow();
    }
}
