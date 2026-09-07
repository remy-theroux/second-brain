package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListDocumentsControllerTest {

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

    private void upload(String token, String filename, String content) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file", filename, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
    }

    @Test
    void returns_an_empty_list_for_an_empty_knowledge_base() throws Exception {
        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returns_the_name_the_status_and_the_date_of_each_document() throws Exception {
        upload(aliceToken, "rapport.pdf", "contenu");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNotEmpty())
                .andExpect(jsonPath("$[0].filename").value("rapport.pdf"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty());
    }

    @Test
    void returns_the_documents_from_the_most_recent_to_the_oldest() throws Exception {
        upload(aliceToken, "ancien.pdf", "premier");
        upload(aliceToken, "recent.pdf", "second");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filename").value("recent.pdf"))
                .andExpect(jsonPath("$[1].filename").value("ancien.pdf"));
    }

    @Test
    void does_not_show_the_documents_of_another_account() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        upload(KnowledgeFixture.token(accessTokenIssuer, bob), "chez-bob.pdf", "contenu");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void refuses_the_list_without_a_token() throws Exception {
        mockMvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void exposes_the_reason_of_a_failed_document() throws Exception {
        upload(aliceToken, "scan.pdf", "un contenu quelconque");
        Document document = documentRepository.findAllByOwnerId(alice).getFirst();
        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("FAILED"))
                .andExpect(jsonPath("$[0].errorMessage").value("Ce document ne contient pas de texte exploitable."));
    }

    @Test
    void exposes_no_reason_for_a_pending_document() throws Exception {
        upload(aliceToken, "notes.md", "un contenu quelconque");

        mockMvc.perform(get("/api/documents").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].errorMessage").doesNotExist());
    }
}
