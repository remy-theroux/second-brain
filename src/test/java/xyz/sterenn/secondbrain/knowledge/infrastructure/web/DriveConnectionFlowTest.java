package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveAuthorizationConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveAuthorizationConfiguration.FakeGoogleDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.application.command.MarkDriveConnectionExpired;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({
    TestcontainersConfiguration.class,
    RecordingNotificationSenderConfiguration.class,
    FakeGoogleDriveAuthorizationConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DriveConnectionFlowTest {

    private static final String PASSWORD = "chevalpile42";

    private static final String AUTHORIZATION_CODE = "4/code-rendu-par-google";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private FakeGoogleDriveAuthorization fakeGoogleDriveAuthorization;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

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
        fakeGoogleDriveAuthorization.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    @AfterEach
    void erase_the_originals() {
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    private String openAnAuthorization() throws Exception {
        String body = mockMvc.perform(
                        post("/api/drive/authorizations").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String consentUrl = JsonPath.read(body, "$.authorizationUrl");
        assertThat(consentUrl).startsWith(FakeGoogleDriveAuthorization.CONSENT_PREFIX);
        return consentUrl.substring(FakeGoogleDriveAuthorization.CONSENT_PREFIX.length());
    }

    private void comeBackFromGoogle(String state, String expectedCode) throws Exception {
        mockMvc.perform(get("/drive/callback").param("state", state).param("code", AUTHORIZATION_CODE))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/documents?drive=" + expectedCode));
    }

    private void connect(String googleEmail, String refreshToken) throws Exception {
        fakeGoogleDriveAuthorization.willGrant(googleEmail, refreshToken);
        comeBackFromGoogle(openAnAuthorization(), "ok");
    }

    @Test
    void connects_a_google_account_on_first_authorization() throws Exception {
        connect("alice@gmail.com", "1//jeton-alice");

        assertThat(fakeGoogleDriveAuthorization.exchangedCodes()).containsExactly(AUTHORIZATION_CODE);
        mockMvc.perform(get("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEmail").value("alice@gmail.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.connectedAt").exists());
    }

    @Test
    void replaces_the_previous_connection_rather_than_adding_a_second() throws Exception {
        connect("alice@gmail.com", "1//jeton-alice");

        connect("alice-pro@gmail.com", "1//jeton-alice-pro");

        assertThat(driveConnectionRepository.findByOwnerId(alice)).get().satisfies(connection -> {
            assertThat(connection.getGoogleEmail()).isEqualTo("alice-pro@gmail.com");
            assertThat(connection.getStatus()).isEqualTo(DriveConnectionStatus.ACTIVE);
        });
        mockMvc.perform(get("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googleEmail").value("alice-pro@gmail.com"));
    }

    /**
     * The row is reused, and a change position belongs to the Drive that handed it out: kept
     * across a reconnection on another Google account, it is one Google refuses in 400 rather
     * than in 410, so no round would ever fall back on the full scan a dead position is owed.
     */
    @Test
    void forgets_the_change_position_of_the_drive_it_replaces() throws Exception {
        connect("alice@gmail.com", "1//jeton-alice");
        DriveConnection connection =
                driveConnectionRepository.findByOwnerId(alice).orElseThrow();
        connection.keepChangesPageToken("1789");
        driveConnectionRepository.save(connection);

        connect("alice-pro@gmail.com", "1//jeton-alice-pro");

        assertThat(driveConnectionRepository.findByOwnerId(alice))
                .get()
                .extracting(DriveConnection::getChangesPageToken)
                .isEqualTo(Optional.empty());
    }

    @Test
    void reports_a_connection_whose_access_was_revoked_as_needing_a_reconnection() throws Exception {
        connect("alice@gmail.com", "1//jeton-alice");

        commandBus.dispatch(new MarkDriveConnectionExpired(alice));

        mockMvc.perform(get("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEEDS_RECONNECTION"));
    }

    @Test
    void forgets_the_connection_without_touching_the_documents() throws Exception {
        upload("rapport.pdf");
        connect("alice@gmail.com", "1//jeton-alice");

        mockMvc.perform(delete("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNoContent());

        assertThat(driveConnectionRepository.findByOwnerId(alice)).isEmpty();
        assertThat(documentRepository.findAllByOwnerId(alice)).hasSize(1);
        mockMvc.perform(get("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void reports_that_no_access_was_granted_when_the_consent_is_denied() throws Exception {
        String state = openAnAuthorization();

        mockMvc.perform(get("/drive/callback").param("state", state).param("error", "access_denied"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "/documents?drive=refus"));

        assertThat(fakeGoogleDriveAuthorization.exchangedCodes()).isEmpty();
        assertThat(driveConnectionRepository.findByOwnerId(alice)).isEmpty();
    }

    @Test
    void refuses_a_callback_whose_state_does_not_match_the_request() throws Exception {
        openAnAuthorization();

        comeBackFromGoogle(DriveAuthorizationState.random().value(), "lien-invalide");

        assertThat(fakeGoogleDriveAuthorization.exchangedCodes()).isEmpty();
        assertThat(driveConnectionRepository.findByOwnerId(alice)).isEmpty();
    }

    @Test
    void reports_a_failure_when_google_cannot_be_reached() throws Exception {
        fakeGoogleDriveAuthorization.willBeUnavailable();
        String state = openAnAuthorization();

        comeBackFromGoogle(state, "echec");

        assertThat(fakeGoogleDriveAuthorization.exchangedCodes()).containsExactly(AUTHORIZATION_CODE);
        assertThat(driveConnectionRepository.findByOwnerId(alice)).isEmpty();
    }

    @Test
    void connects_the_account_that_opened_the_request_whoever_plays_the_callback() throws Exception {
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        fakeGoogleDriveAuthorization.willGrant("alice@gmail.com", "1//jeton-alice");

        // Played without any token, as Google would: the owner comes from the request found by
        // its state, which is what protects an unauthenticated callback.
        comeBackFromGoogle(openAnAuthorization(), "ok");

        assertThat(driveConnectionRepository.findByOwnerId(alice))
                .get()
                .satisfies(connection -> assertThat(connection.getGoogleEmail()).isEqualTo("alice@gmail.com"));
        assertThat(driveConnectionRepository.findByOwnerId(bob)).isEmpty();
    }

    @Test
    void neither_shows_nor_deletes_the_connection_of_another_account() throws Exception {
        connect("alice@gmail.com", "1//jeton-alice");
        UUID bob = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);
        String bobToken = KnowledgeFixture.token(accessTokenIssuer, bob);

        mockMvc.perform(get("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/drive/connection").header(HttpHeaders.AUTHORIZATION, "Bearer " + bobToken))
                .andExpect(status().isNotFound());

        assertThat(driveConnectionRepository.findByOwnerId(alice)).isPresent();
    }

    @Test
    void refuses_a_callback_replaying_a_state_already_consumed() throws Exception {
        String state = openAnAuthorization();
        comeBackFromGoogle(state, "ok");

        comeBackFromGoogle(state, "lien-invalide");

        assertThat(fakeGoogleDriveAuthorization.exchangedCodes()).containsExactly(AUTHORIZATION_CODE);
    }

    @Test
    void refuses_an_anonymous_request() throws Exception {
        mockMvc.perform(post("/api/drive/authorizations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/drive/connection")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/drive/connection")).andExpect(status().isUnauthorized());
    }

    private void upload(String filename) throws Exception {
        mockMvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile(
                                "file",
                                filename,
                                "application/octet-stream",
                                filename.getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isCreated());
    }
}
