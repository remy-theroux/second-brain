package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive.folder;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.port.AccessTokenIssuer;

/**
 * Deliberately without {@code @Transactional}: the watching transaction really has to roll back
 * for the status written by the second one to prove anything. See ADR-0028. Hence the explicit
 * cleanup in {@code @AfterEach} — the cascades take the connection and its folders with the account.
 */
@Import({
    TestcontainersConfiguration.class,
    RecordingNotificationSenderConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
class WatchedFolderRevocationTest {

    private static final String EMAIL = "gaston@exemple.fr";

    private static final String WATCHED_FOLDERS = "/api/drive/watched-folders";

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

    @Autowired
    private WatchedFolderRepository watchedFolderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID gaston;
    private String gastonToken;

    @BeforeEach
    void prepare_a_signed_in_account_and_its_drive() {
        recordingNotificationSender.clear();
        fakeGoogleDrive.clear();
        gaston = AccountFixture.registerVerified(commandBus, recordingNotificationSender, EMAIL, "chevalpile42");
        gastonToken = KnowledgeFixture.token(accessTokenIssuer, gaston);
    }

    @AfterEach
    void erases_what_was_committed() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
    }

    @Test
    void keeps_the_status_written_by_the_second_transaction_when_the_watching_one_rolls_back() throws Exception {
        DriveConnection connection = driveConnectionRepository.save(
                DriveConnection.connect(gaston, "gaston@gmail.com", new RefreshToken("1//jeton"), Instant.now()));
        fakeGoogleDrive.put(DriveFolder.ROOT, folder("a1", "Notes"));
        fakeGoogleDrive.willHaveHadItsAccessWithdrawn();

        mockMvc.perform(post(WATCHED_FOLDERS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderId\": \"a1\"}")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + gastonToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DriveAuthorizationRevokedException.MESSAGE));

        assertThat(driveConnectionRepository.findByOwnerId(gaston)).get().satisfies(reloaded -> assertThat(
                        reloaded.getStatus())
                .isEqualTo(DriveConnectionStatus.NEEDS_RECONNECTION));
        assertThat(watchedFolderRepository.findAllByConnectionId(connection.getId()))
                .isEmpty();
    }
}
