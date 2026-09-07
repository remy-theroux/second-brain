package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * A refusal is always the <em>last</em> HTTP call of its test: the exception marks the enclosing
 * transaction rollback-only, and a second call behind it would fail on an
 * {@code UnexpectedRollbackException}. Whatever is left to check after a refusal is read through
 * the port.
 */
@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UploadDocumentControllerTest {

    private static final String PASSWORD = "chevalpile42";
    private static final byte[] CONTENT = "le contenu du rapport".getBytes(StandardCharsets.UTF_8);

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
    private DocumentStorage documentStorage;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID account;
    private String token;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        account =
                AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        token = KnowledgeFixture.token(accessTokenIssuer, account);
    }

    @AfterEach
    void erase_the_originals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    private static MockMultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "application/octet-stream", content);
    }

    private void upload(String filename, byte[] content) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(file(filename, content))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    void records_an_uploaded_document_as_awaiting_processing() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(file("rapport.pdf", CONTENT))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());

        assertThat(documentRepository.findAllByOwnerId(account)).singleElement().satisfies(document -> {
            assertThat(document.getFilename()).isEqualTo("rapport.pdf");
            assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        });
    }

    @Test
    void keeps_the_original_file() throws Exception {
        upload("rapport.pdf", CONTENT);

        UUID document = documentRepository.findAllByOwnerId(account).getFirst().getId();

        assertThat(documentStorage.read(document))
                .hasValueSatisfying(stored -> assertThat(stored).isEqualTo(CONTENT));
    }

    @Test
    void refuses_an_already_present_content_by_naming_the_existing_document() throws Exception {
        upload("rapport.pdf", CONTENT);
        UUID existing = documentRepository.findAllByOwnerId(account).getFirst().getId();

        mockMvc.perform(multipart("/api/documents")
                        .file(file("copie-du-rapport.pdf", CONTENT))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingDocumentId").value(existing.toString()))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertThat(documentRepository.findAllByOwnerId(account)).hasSize(1);
    }

    @Test
    void lets_another_account_upload_the_same_content() throws Exception {
        upload("rapport.pdf", CONTENT);
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);

        mockMvc.perform(multipart("/api/documents")
                        .file(file("rapport.pdf", CONTENT))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + KnowledgeFixture.token(accessTokenIssuer, bob)))
                .andExpect(status().isCreated());

        assertThat(documentRepository.findAllByOwnerId(bob)).hasSize(1);
    }

    @Test
    void refuses_an_unsupported_format_by_stating_the_accepted_formats() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(file("programme.exe", CONTENT))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(".pdf")));

        assertThat(documentRepository.findAllByOwnerId(account)).isEmpty();
    }

    @Test
    void refuses_an_empty_file() throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(file("rapport.pdf", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.file").isNotEmpty());
    }

    @Test
    void refuses_an_upload_without_a_token() throws Exception {
        mockMvc.perform(multipart("/api/documents").file(file("rapport.pdf", CONTENT)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void attaches_the_document_to_the_token_bearer_and_to_nobody_else() throws Exception {
        UUID bob =
                AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob2@exemple.fr", PASSWORD);

        upload("rapport.pdf", CONTENT);

        assertThat(documentRepository.findAllByOwnerId(account)).hasSize(1);
        assertThat(documentRepository.findAllByOwnerId(bob)).isEmpty();
    }

    @Test
    void accepts_the_four_announced_formats() throws Exception {
        upload("rapport.pdf", "un".getBytes(StandardCharsets.UTF_8));
        upload("notes.md", "deux".getBytes(StandardCharsets.UTF_8));
        upload("brouillon.txt", "trois".getBytes(StandardCharsets.UTF_8));
        upload("contrat.docx", "quatre".getBytes(StandardCharsets.UTF_8));

        assertThat(documentRepository.findAllByOwnerId(account))
                .extracting(Document::getFilename)
                .containsExactlyInAnyOrder("rapport.pdf", "notes.md", "brouillon.txt", "contrat.docx");
    }
}
