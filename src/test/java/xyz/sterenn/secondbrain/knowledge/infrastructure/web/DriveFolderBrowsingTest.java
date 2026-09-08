package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive.folder;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveNotConnectedException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

@Import({
    TestcontainersConfiguration.class,
    RecordingNotificationSenderConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DriveFolderBrowsingTest {

    private static final String PASSWORD = "chevalpile42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private FakeGoogleDrive fakeGoogleDrive;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    private UUID alice;
    private String aliceToken;

    @BeforeEach
    void prepare_a_signed_in_account() {
        recordingNotificationSender.clear();
        fakeGoogleDrive.clear();
        alice = AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);
        aliceToken = KnowledgeFixture.token(accessTokenIssuer, alice);
    }

    private void connectADrive() {
        driveConnectionRepository.save(
                DriveConnection.connect(alice, "alice@gmail.com", new RefreshToken("1//jeton-alice"), Instant.now()));
    }

    @Test
    void lists_the_top_level_folders() throws Exception {
        connectADrive();
        fakeGoogleDrive.put(DriveFolder.ROOT, folder("a1", "Notes"), folder("a2", "Factures"));

        mockMvc.perform(get("/api/drive/folders").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("a1"))
                .andExpect(jsonPath("$[0].name").value("Notes"))
                .andExpect(jsonPath("$[1].name").value("Factures"));
    }

    @Test
    void lists_the_subfolders_of_a_folder() throws Exception {
        connectADrive();
        fakeGoogleDrive.put(DriveFolder.ROOT, folder("a1", "Notes"));
        fakeGoogleDrive.put("a1", folder("b1", "2025"), folder("b2", "2026"));

        mockMvc.perform(get("/api/drive/folders")
                        .param("parent", "a1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("b1"))
                .andExpect(jsonPath("$[1].id").value("b2"));
    }

    @Test
    void marks_the_connection_as_needing_a_reconnection_when_google_reports_a_revoked_access() throws Exception {
        connectADrive();
        fakeGoogleDrive.willReportARevokedAuthorization();

        mockMvc.perform(get("/api/drive/folders").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isConflict());

        // The refusal rolled the browsing transaction back: the status is read by the port, not
        // by a second request.
        assertThat(driveConnectionRepository.findByOwnerId(alice)).get().satisfies(connection -> assertThat(
                        connection.getStatus())
                .isEqualTo(DriveConnectionStatus.NEEDS_RECONNECTION));
    }

    @Test
    void marks_the_connection_as_needing_a_reconnection_when_the_renewal_of_a_refused_token_reveals_it()
            throws Exception {
        connectADrive();
        fakeGoogleDrive.put(DriveFolder.ROOT, folder("a1", "Notes"));
        fakeGoogleDrive.willHaveHadItsAccessWithdrawn();

        mockMvc.perform(get("/api/drive/folders").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DriveAuthorizationRevokedException.MESSAGE));

        assertThat(driveConnectionRepository.findByOwnerId(alice)).get().satisfies(connection -> assertThat(
                        connection.getStatus())
                .isEqualTo(DriveConnectionStatus.NEEDS_RECONNECTION));
    }

    @Test
    void browses_again_with_a_token_bought_after_the_refusal_of_the_kept_one() throws Exception {
        connectADrive();
        fakeGoogleDrive.put(DriveFolder.ROOT, folder("a1", "Notes"));
        fakeGoogleDrive.willRejectTheCachedAccessToken();

        mockMvc.perform(get("/api/drive/folders").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Notes"));
    }

    @Test
    void refuses_to_browse_without_a_connected_account() throws Exception {
        mockMvc.perform(get("/api/drive/folders").header(HttpHeaders.AUTHORIZATION, "Bearer " + aliceToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DriveNotConnectedException.MESSAGE));
    }

    @Test
    void refuses_an_anonymous_request() throws Exception {
        mockMvc.perform(get("/api/drive/folders")).andExpect(status().isUnauthorized());
    }
}
