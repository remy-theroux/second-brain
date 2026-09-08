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
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration;
import xyz.sterenn.secondbrain.knowledge.FakeGoogleDriveConfiguration.FakeGoogleDrive;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.KnowledgeFixture;
import xyz.sterenn.secondbrain.knowledge.RecordingEmbeddingPortConfiguration;
import xyz.sterenn.secondbrain.knowledge.application.command.DisconnectDrive;
import xyz.sterenn.secondbrain.knowledge.application.command.RenewDriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.DriveChannelPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * The subscription that outlives its own deadline, and the one that dies with the Drive. The
 * clock never triggers anything here either: the renewal interval is pushed to a day in the test
 * properties and the round is dispatched by hand.
 */
@Import({
    TestcontainersConfiguration.class,
    RecordingEmbeddingPortConfiguration.class,
    FakeGoogleDriveConfiguration.class
})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("worker")
class DriveChannelRenewalTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String EMAIL = "alice-canal@exemple.fr";

    private static final DriveFolder NOTES = new DriveFolder("a1", "Notes");

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private FakeGoogleDrive fakeGoogleDrive;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private DriveChannelRepository driveChannelRepository;

    @Autowired
    private WatchedFolderRepository watchedFolderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScheduledTaskHolder scheduledTaskHolder;

    @Autowired
    private S3Client s3Client;

    @Value("${secondbrain.storage.s3.bucket}")
    private String originalsBucket;

    private UUID alice;
    private UUID connectionId;

    @BeforeEach
    void prepare_a_connected_drive() {
        fakeGoogleDrive.clear();
        alice = userRepository
                .save(User.register(new Email(EMAIL), "empreinte"))
                .getId();
        connectionId = driveConnectionRepository
                .save(DriveConnection.connect(alice, "alice@gmail.com", new RefreshToken("1//jeton"), Instant.now()))
                .getId();
    }

    @AfterEach
    void erase_what_has_been_committed() {
        jdbcTemplate.update("DELETE FROM users_users WHERE email = ?", EMAIL);
        KnowledgeFixture.emptyTheOriginals(s3Client, originalsBucket);
    }

    /**
     * The order is the whole point: closing first would leave a window with no subscription at
     * all, and a change that moved through it would wait for the periodic scan.
     */
    @Test
    void opens_a_new_channel_before_the_current_one_expires() {
        DriveChannel expiring = aChannelExpiringAt(Instant.now().plus(Duration.ofHours(1)));

        commandBus.dispatch(new RenewDriveChannels());

        DriveChannel renewed = channel();
        assertThat(renewed.getChannelId()).isNotEqualTo(expiring.getChannelId());
        assertThat(renewed.getExpiresAt()).isAfter(Instant.now().plus(DriveChannelPolicy.RENEWAL_MARGIN));
        assertThat(fakeGoogleDrive.channelCalls()).containsExactly("watch:" + renewed.getChannelId(), stopOf(expiring));
    }

    /** Two live channels on one Drive is two notifications per change, until the old one lapses. */
    @Test
    void closes_the_channel_it_replaces() {
        DriveChannel expiring = aChannelExpiringAt(Instant.now().plus(Duration.ofHours(1)));

        commandBus.dispatch(new RenewDriveChannels());

        assertThat(fakeGoogleDrive.channelCalls()).contains(stopOf(expiring));
        assertThat(fakeGoogleDrive.openChannels()).containsExactly(channel().getChannelId());
        assertThat(driveChannelRepository.findByChannelId(expiring.getChannelId()))
                .isEmpty();
    }

    /** Nothing opens a channel at the consent screen: the first round after a connection does. */
    @Test
    void opens_a_channel_for_a_connection_that_has_none() {
        commandBus.dispatch(new RenewDriveChannels());

        DriveChannel opened = channel();
        assertThat(opened.getResourceId()).isEqualTo("ressource-" + opened.getChannelId());
        assertThat(opened.getExpiresAt()).isNotNull();
        assertThat(fakeGoogleDrive.channelCalls()).containsExactly("watch:" + opened.getChannelId());
    }

    /** A channel renewed every round is a stop and a watch paid for nothing, seven days too early. */
    @Test
    void leaves_alone_a_channel_that_is_not_due_yet() {
        DriveChannel comfortable = aChannelExpiringAt(Instant.now().plus(Duration.ofDays(3)));

        commandBus.dispatch(new RenewDriveChannels());

        assertThat(channel().getChannelId()).isEqualTo(comfortable.getChannelId());
        assertThat(fakeGoogleDrive.channelCalls()).isEmpty();
    }

    /**
     * The promise of the whole ticket: the push is a fast path, never the only one. The channel
     * lapsed, no notification will ever be accepted on it again, and the file still lands.
     */
    @Test
    void catches_up_through_the_periodic_scan_when_the_channel_has_expired() {
        DriveChannel lapsed = aChannelExpiringAt(Instant.now().minus(Duration.ofHours(1)));
        UUID watchedFolderId = watchedFolderRepository
                .save(WatchedFolder.watch(connectionId, NOTES, Instant.now()))
                .getId();
        fakeGoogleDrive.putFile("a1", "f1", "nouveau.md", Fixtures.read(Fixtures.STRUCTURED_MD));
        fakeGoogleDrive.willReportChanges(fakeGoogleDrive.changeOn("f1", "a1"));

        assertThat(lapsed.accepts(lapsed.getToken(), Instant.now())).isFalse();
        requestASynchronisation();

        await().atMost(TIMEOUT)
                .untilAsserted(() -> assertThat(documents()).singleElement().satisfies(document -> {
                    assertThat(document.getFilename()).isEqualTo("nouveau.md");
                    assertThat(document.getWatchedFolderId()).isEqualTo(watchedFolderId);
                }));
        assertThat(scheduledTasksNamed("requestASynchronisation")).hasSize(1);
    }

    /** Left open, Google keeps notifying a Drive nobody reads any more, until its own deadline. */
    @Test
    void closes_the_channel_when_the_drive_is_disconnected() {
        DriveChannel channel = aChannelExpiringAt(Instant.now().plus(Duration.ofDays(3)));

        commandBus.dispatch(new DisconnectDrive(alice));

        assertThat(fakeGoogleDrive.channelCalls()).containsExactly(stopOf(channel));
        assertThat(driveChannelRepository.findByChannelId(channel.getChannelId()))
                .isEmpty();
    }

    /** Read rather than awaited, like the synchronisation round: a delay, never a rate. */
    @Test
    void puts_the_renewals_on_a_fixed_delay_clock() {
        assertThat(scheduledTasksNamed("renewTheChannels")).singleElement().satisfies(task -> assertThat(task.getTask())
                .isInstanceOf(FixedDelayTask.class));
    }

    private DriveChannel aChannelExpiringAt(Instant expiresAt) {
        DriveChannel channel = DriveChannel.open(connectionId, Instant.now());
        channel.subscribed("ressource-google", expiresAt);
        return driveChannelRepository.save(channel);
    }

    private DriveChannel channel() {
        return driveChannelRepository.findByConnectionId(connectionId).orElseThrow();
    }

    private static String stopOf(DriveChannel channel) {
        return "stop:" + channel.getChannelId() + "/" + channel.getResourceId();
    }

    private void requestASynchronisation() {
        rabbitTemplate.convertAndSend(
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.drive-synchronisation.requested",
                new DriveSynchronisationRequested(alice, Instant.now()));
    }

    private List<Document> documents() {
        return documentRepository.findAllByOwnerId(alice);
    }

    private List<ScheduledTask> scheduledTasksNamed(String method) {
        return scheduledTaskHolder.getScheduledTasks().stream()
                .filter(task -> task.getTask().toString().contains(method))
                .toList();
    }
}
