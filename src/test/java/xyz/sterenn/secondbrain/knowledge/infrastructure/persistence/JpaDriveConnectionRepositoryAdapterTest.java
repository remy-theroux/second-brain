package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveAuthorizationRequest;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveAuthorizationRequestRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveConnectionStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaDriveConnectionRepositoryAdapterTest {

    private static final RefreshToken TOKEN = new RefreshToken("1//04-un-vrai-jeton-de-rafraichissement");

    private static final Instant CONNECTED_AT = Instant.parse("2026-09-08T10:00:00Z");

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private DriveAuthorizationRequestRepository driveAuthorizationRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID anAccount(String email) {
        return userRepository.save(User.register(new Email(email), "empreinte")).getId();
    }

    @Test
    void enciphers_the_refresh_token_in_its_column() {
        // The only observable proof that RefreshTokenAttributeConverter is applied by Hibernate:
        // a unit test of the converter passes even when nothing calls it.
        UUID owner = anAccount("drive-chiffre@exemple.fr");

        driveConnectionRepository.save(DriveConnection.connect(owner, "compte@gmail.com", TOKEN, CONNECTED_AT));

        String column = jdbcTemplate.queryForObject(
                "SELECT refresh_token FROM knowledge_drive_connections WHERE owner_id = ?", String.class, owner);
        assertThat(column).isNotBlank().doesNotContain(TOKEN.value());
    }

    @Test
    void reads_back_the_connection_of_an_owner() {
        UUID owner = anAccount("drive-relecture@exemple.fr");
        driveConnectionRepository.save(DriveConnection.connect(owner, "compte@gmail.com", TOKEN, CONNECTED_AT));

        assertThat(driveConnectionRepository.findByOwnerId(owner)).get().satisfies(connection -> {
            assertThat(connection.getGoogleEmail()).isEqualTo("compte@gmail.com");
            assertThat(connection.getRefreshToken()).isEqualTo(TOKEN);
            assertThat(connection.getStatus()).isEqualTo(DriveConnectionStatus.ACTIVE);
            assertThat(connection.getConnectedAt()).isEqualTo(CONNECTED_AT);
        });
        assertThat(driveConnectionRepository.findByOwnerId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void keeps_the_page_token_the_next_run_starts_from() {
        UUID owner = anAccount("drive-jeton-de-page@exemple.fr");
        DriveConnection connection =
                driveConnectionRepository.save(DriveConnection.connect(owner, "compte@gmail.com", TOKEN, CONNECTED_AT));
        assertThat(connection.getChangesPageToken()).isEmpty();

        connection.keepChangesPageToken("1900");
        driveConnectionRepository.save(connection);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT changes_page_token FROM knowledge_drive_connections WHERE owner_id = ?",
                        String.class,
                        owner))
                .isEqualTo("1900");
    }

    @Test
    void forgets_the_page_token_google_no_longer_knows() {
        UUID owner = anAccount("drive-jeton-perime@exemple.fr");
        DriveConnection connection =
                driveConnectionRepository.save(DriveConnection.connect(owner, "compte@gmail.com", TOKEN, CONNECTED_AT));
        connection.keepChangesPageToken("1900");
        driveConnectionRepository.save(connection);

        connection.forgetChangesPageToken();
        driveConnectionRepository.save(connection);

        assertThat(driveConnectionRepository.findByOwnerId(owner)).get().satisfies(reloaded -> assertThat(
                        reloaded.getChangesPageToken())
                .isEmpty());
    }

    @Test
    void forgets_a_connection() {
        UUID owner = anAccount("drive-oubli@exemple.fr");
        DriveConnection connection =
                driveConnectionRepository.save(DriveConnection.connect(owner, "compte@gmail.com", TOKEN, CONNECTED_AT));

        driveConnectionRepository.delete(connection);

        assertThat(driveConnectionRepository.findByOwnerId(owner)).isEmpty();
    }

    @Test
    void finds_an_authorization_request_by_its_state() {
        UUID owner = anAccount("drive-demande@exemple.fr");
        DriveAuthorizationRequest request = driveAuthorizationRequestRepository.save(
                DriveAuthorizationRequest.open(owner, DriveAuthorizationState.random(), CONNECTED_AT));

        assertThat(driveAuthorizationRequestRepository.findByState(request.getState()))
                .get()
                .satisfies(reloaded -> assertThat(reloaded.getOwnerId()).isEqualTo(owner));
    }
}
