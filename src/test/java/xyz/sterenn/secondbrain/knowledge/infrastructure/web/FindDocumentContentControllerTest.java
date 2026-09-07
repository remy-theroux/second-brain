package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class FindDocumentContentControllerTest {

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
    void returns_the_uploaded_bytes_unchanged() throws Exception {
        Document document = upload(aliceToken, alice, "structure.md", Fixtures.STRUCTURED_MD);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Fixtures.read(Fixtures.STRUCTURED_MD)));
    }

    @Test
    void announces_the_media_type_of_the_format() throws Exception {
        Document document = upload(aliceToken, alice, "signets.pdf", Fixtures.BOOKMARKS_PDF);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/pdf")));
    }

    @Test
    void offers_the_file_as_an_attachment_under_its_original_name() throws Exception {
        Document document = upload(aliceToken, alice, "notes.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("notes.txt")));
    }

    @Test
    void percent_encodes_an_accented_name_in_the_header() throws Exception {
        Document document = upload(aliceToken, alice, "été.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''%C3%A9t%C3%A9.txt")));
    }

    @Test
    void returns_the_original_of_a_document_whose_processing_failed() throws Exception {
        Document document = upload(aliceToken, alice, "scan.txt", Fixtures.RAW_TXT);
        document.markProcessingFailed("Ce document ne contient pas de texte exploitable.");
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(content().bytes(Fixtures.read(Fixtures.RAW_TXT)));
    }

    @Test
    void says_the_original_is_gone_rather_than_the_document() throws Exception {
        Document document = upload(aliceToken, alice, "disparu.txt", Fixtures.RAW_TXT);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);

        mockMvc.perform(get("/api/documents/" + document.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("L'original de ce document n'est plus disponible."));
    }

    @Test
    void makes_an_unknown_id_not_found() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce document est introuvable dans votre base de connaissance."));
    }

    @Test
    void makes_the_document_of_another_account_not_found() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        Document bobsDocument =
                upload(KnowledgeFixture.token(accessTokenIssuer, bob), bob, "chez-bob.txt", Fixtures.RAW_TXT);

        mockMvc.perform(get("/api/documents/" + bobsDocument.getId() + "/content")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Ce document est introuvable dans votre base de connaissance."));
    }

    @Test
    void refuses_the_download_without_a_token() throws Exception {
        mockMvc.perform(get("/api/documents/" + UUID.randomUUID() + "/content")).andExpect(status().isUnauthorized());
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
