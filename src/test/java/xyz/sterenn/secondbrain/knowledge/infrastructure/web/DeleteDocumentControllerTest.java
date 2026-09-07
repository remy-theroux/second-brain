package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentStorage;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DeleteDocumentControllerTest {

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
    private DocumentStorage documentStorage;

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

    private UUID upload(String token, UUID ownerId, String filename) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file",
                                filename,
                                "application/octet-stream",
                                filename.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
        return documentRepository.findAllByOwnerId(ownerId).getFirst().getId();
    }

    @Test
    void removes_the_document_from_the_list_and_deletes_its_original_file() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf");

        mockMvc.perform(delete("/api/documents/{id}", document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        assertThat(documentRepository.findAllByOwnerId(alice)).isEmpty();
        assertThat(documentStorage.read(document)).isEmpty();
    }

    @Test
    void allows_re_uploading_a_content_after_deleting_it() throws Exception {
        UUID document = upload(aliceToken, alice, "rapport.pdf");
        mockMvc.perform(delete("/api/documents/{id}", document)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        upload(aliceToken, alice, "rapport.pdf");

        assertThat(documentRepository.findAllByOwnerId(alice)).hasSize(1);
    }

    @Test
    void refuses_to_delete_the_document_of_another_account_as_if_it_did_not_exist() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        UUID bobsDocument = upload(KnowledgeFixture.token(accessTokenIssuer, bob), bob, "chez-bob.pdf");

        mockMvc.perform(delete("/api/documents/{id}", bobsDocument)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());

        assertThat(documentRepository.findAllByOwnerId(bob)).hasSize(1);
    }

    @Test
    void refuses_to_delete_a_nonexistent_document() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refuses_a_deletion_without_a_token() throws Exception {
        mockMvc.perform(delete("/api/documents/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
