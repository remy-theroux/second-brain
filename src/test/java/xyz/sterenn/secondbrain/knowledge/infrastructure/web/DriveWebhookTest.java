package xyz.sterenn.secondbrain.knowledge.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.event.DriveSynchronisationRequested;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.shared.event.amqp.AmqpConfiguration;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

/**
 * No {@code @Transactional}: the announcement only leaves at commit, and a test transaction would
 * keep it from leaving. No bearer token is ever sent either, which is what proves the route is
 * declared public in {@code SecurityConfig} — undeclared, everything below would answer 401.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DriveWebhookTest {

    private static final String ALICE = "alice-webhook@exemple.fr";

    private static final String BOB = "bob-webhook@exemple.fr";

    private static final String OBSERVATION = "test.observation.synchronisation-drive";

    private static final Duration ANNOUNCED = Duration.ofSeconds(5);

    /** Long enough for an announcement that left at commit, short enough to prove an absence. */
    private static final Duration NOT_ANNOUNCED = Duration.ofSeconds(1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private DriveChannelRepository driveChannelRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID alice;
    private DriveChannel aliceChannel;

    @BeforeEach
    void prepare_a_connected_drive_and_its_channel() {
        amqpAdmin.declareQueue(new Queue(OBSERVATION));
        amqpAdmin.declareBinding(new Binding(
                OBSERVATION,
                Binding.DestinationType.QUEUE,
                AmqpConfiguration.EVENTS_EXCHANGE,
                "knowledge.drive-synchronisation.requested",
                null));
        alice = anAccount(ALICE);
        aliceChannel = aChannelOn(alice);
    }

    @AfterEach
    void erase_what_was_committed() {
        amqpAdmin.deleteQueue(OBSERVATION);
        jdbcTemplate.update("DELETE FROM users_users WHERE email IN (?, ?)", ALICE, BOB);
    }

    /**
     * The whole point of the ticket: a notification asks for a reading, it never says what moved.
     * What leaves is exactly the event the scheduled round publishes, and no document is imported
     * on the notification's word alone.
     */
    @Test
    void reads_the_changes_of_the_drive_when_a_notification_arrives() throws Exception {
        mockMvc.perform(aNotification(aliceChannel.getChannelId(), aliceChannel.getToken(), "change"))
                .andExpect(status().isOk());

        assertThat(announcement()).isNotNull().satisfies(event -> assertThat(event.ownerId())
                .isEqualTo(alice));
        assertThat(documentRepository.findAllByOwnerId(alice)).isEmpty();
    }

    @Test
    void refuses_a_notification_that_does_not_carry_the_channel_token() throws Exception {
        mockMvc.perform(post("/api/drive/notifications")
                        .header(NotifyDriveChangeController.CHANNEL_ID_HEADER, aliceChannel.getChannelId())
                        .header(NotifyDriveChangeController.RESOURCE_STATE_HEADER, "change"))
                .andExpect(status().isNotFound());

        assertThat(noAnnouncement()).isNull();
    }

    @Test
    void refuses_a_notification_whose_token_belongs_to_another_channel() throws Exception {
        DriveChannel bobChannel = aChannelOn(anAccount(BOB));

        mockMvc.perform(aNotification(aliceChannel.getChannelId(), bobChannel.getToken(), "change"))
                .andExpect(status().isNotFound());

        assertThat(noAnnouncement()).isNull();
    }

    @Test
    void refuses_a_notification_on_a_channel_that_nobody_opened() throws Exception {
        mockMvc.perform(aNotification(UUID.randomUUID().toString(), aliceChannel.getToken(), "change"))
                .andExpect(status().isNotFound());

        assertThat(noAnnouncement()).isNull();
    }

    /**
     * Google closes a channel that answers in error repeatedly, so a Drive nobody can read any
     * more must not cost the channel a 500: the row goes, the answer stays a 200.
     */
    @Test
    void ignores_a_notification_for_a_connection_that_was_revoked_and_closes_the_channel() throws Exception {
        DriveConnection connection =
                driveConnectionRepository.findByOwnerId(alice).orElseThrow();
        connection.markNeedsReconnection();
        driveConnectionRepository.save(connection);

        mockMvc.perform(aNotification(aliceChannel.getChannelId(), aliceChannel.getToken(), "change"))
                .andExpect(status().isOk());

        assertThat(noAnnouncement()).isNull();
        assertThat(driveChannelRepository.findByChannelId(aliceChannel.getChannelId()))
                .isEmpty();
    }

    /** Sent once to validate a brand new channel: read as a change, every renewal would scan for nothing. */
    @Test
    void ignores_the_sync_notification_that_validates_a_new_channel() throws Exception {
        mockMvc.perform(aNotification(aliceChannel.getChannelId(), aliceChannel.getToken(), "sync"))
                .andExpect(status().isOk());

        assertThat(noAnnouncement()).isNull();
    }

    /**
     * The idempotence is the one the synchronisation round already carries, not a second one:
     * what this guards is that the webhook itself imports nothing, however many times it is called.
     */
    @Test
    void ingests_nothing_twice_when_two_notifications_arrive_for_the_same_change() throws Exception {
        MockHttpServletRequestBuilder notification =
                aNotification(aliceChannel.getChannelId(), aliceChannel.getToken(), "change");

        mockMvc.perform(notification).andExpect(status().isOk());
        mockMvc.perform(notification).andExpect(status().isOk());

        assertThat(announcement()).isNotNull();
        assertThat(announcement()).isNotNull();
        assertThat(documentRepository.findAllByOwnerId(alice)).isEmpty();
    }

    /**
     * The branch the ticket rests on: Google unsubscribes a channel that answers in error, so
     * anything falling over on this side must not cost the channel. The row below is one whose
     * ciphertext no longer deciphers — a rotated key — which is a real way to make the real stack
     * throw where a mock would only prove that one line returns what it says.
     */
    @Test
    void answers_200_when_a_notification_cannot_be_handled_at_all() throws Exception {
        String channelId = aChannelWhoseTokenCanNoLongerBeRead(aConnectionOn(anAccount(BOB)));

        mockMvc.perform(aNotification(channelId, aliceChannel.getToken(), "change"))
                .andExpect(status().isOk());

        assertThat(noAnnouncement()).isNull();
    }

    private static MockHttpServletRequestBuilder aNotification(
            String channelId, DriveChannelToken token, String resourceState) {
        return post("/api/drive/notifications")
                .header(NotifyDriveChangeController.CHANNEL_ID_HEADER, channelId)
                .header(NotifyDriveChangeController.CHANNEL_TOKEN_HEADER, token.value())
                .header(NotifyDriveChangeController.RESOURCE_STATE_HEADER, resourceState);
    }

    private DriveSynchronisationRequested announcement() {
        return (DriveSynchronisationRequested) rabbitTemplate.receiveAndConvert(OBSERVATION, ANNOUNCED.toMillis());
    }

    private Object noAnnouncement() {
        return rabbitTemplate.receiveAndConvert(OBSERVATION, NOT_ANNOUNCED.toMillis());
    }

    private UUID anAccount(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    private DriveChannel aChannelOn(UUID owner) {
        DriveChannel channel = DriveChannel.open(aConnectionOn(owner), Instant.now());
        channel.subscribed("ressource-" + owner, Instant.now().plus(Duration.ofDays(7)));
        return driveChannelRepository.save(channel);
    }

    private UUID aConnectionOn(UUID owner) {
        return driveConnectionRepository
                .save(DriveConnection.connect(owner, owner + "@gmail.com", new RefreshToken("1//jeton"), Instant.now()))
                .getId();
    }

    /** Written under the repository, which would refuse to encipher what is not a token. */
    private String aChannelWhoseTokenCanNoLongerBeRead(UUID connectionId) {
        String channelId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO knowledge_drive_channels
                    (connection_id, channel_id, resource_id, token, opened_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)""",
                connectionId,
                channelId,
                "ressource-illisible",
                "ceci n'est pas un chiffre",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plus(Duration.ofDays(7))));
        return channelId;
    }
}
