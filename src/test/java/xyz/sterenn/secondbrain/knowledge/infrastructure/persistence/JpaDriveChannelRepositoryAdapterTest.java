package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaDriveChannelRepositoryAdapterTest {

    private static final Instant OPENED_AT = Instant.parse("2026-09-08T10:00:00Z");

    private static final Instant EXPIRES_AT = OPENED_AT.plus(Duration.ofDays(7));

    @Autowired
    private DriveChannelRepository driveChannelRepository;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void enciphers_the_channel_token_in_its_column() {
        // The only observable proof that DriveChannelTokenAttributeConverter is applied by
        // Hibernate: a unit test of the converter passes even when nothing calls it.
        DriveChannel channel = aChannelOn("canal-chiffre@exemple.fr");

        String column = jdbcTemplate.queryForObject(
                "SELECT token FROM knowledge_drive_channels WHERE id = ?", String.class, channel.getId());

        assertThat(column).isNotBlank().doesNotContain(channel.getToken().value());
    }

    @Test
    void reads_a_channel_back_by_the_identifier_a_notification_carries() {
        DriveChannel channel = aChannelOn("canal-relecture@exemple.fr");

        assertThat(driveChannelRepository.findByChannelId(channel.getChannelId()))
                .get()
                .satisfies(reloaded -> {
                    assertThat(reloaded.getToken()).isEqualTo(channel.getToken());
                    assertThat(reloaded.getResourceId()).isEqualTo("ressource-google");
                    assertThat(reloaded.getExpiresAt()).isEqualTo(EXPIRES_AT);
                    assertThat(reloaded.getConnectionId()).isEqualTo(channel.getConnectionId());
                });
        assertThat(driveChannelRepository.findByChannelId(UUID.randomUUID().toString()))
                .isEmpty();
    }

    @Test
    void loses_its_channel_when_the_connection_is_forgotten() {
        DriveChannel channel = aChannelOn("canal-cascade@exemple.fr");
        DriveConnection connection = driveConnectionRepository
                .findByOwnerId(userRepository
                        .findByEmail(new Email("canal-cascade@exemple.fr"))
                        .orElseThrow()
                        .getId())
                .orElseThrow();

        driveConnectionRepository.delete(connection);

        assertThat(driveChannelRepository.findByChannelId(channel.getChannelId()))
                .isEmpty();
    }

    private DriveChannel aChannelOn(String email) {
        UUID owner = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        DriveConnection connection = driveConnectionRepository.save(
                DriveConnection.connect(owner, "compte@gmail.com", new RefreshToken("1//jeton"), OPENED_AT));
        DriveChannel channel = DriveChannel.open(connection.getId(), OPENED_AT);
        channel.subscribed("ressource-google", EXPIRES_AT);
        return driveChannelRepository.save(channel);
    }
}
