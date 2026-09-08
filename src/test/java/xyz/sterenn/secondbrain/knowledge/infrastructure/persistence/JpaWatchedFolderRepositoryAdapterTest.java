package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.entity.WatchedFolder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.FolderAlreadyCoveredException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveConnectionRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.WatchedFolderRepository;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFolder;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class JpaWatchedFolderRepositoryAdapterTest {

    private static final Instant WATCHED_AT = Instant.parse("2026-09-08T10:00:00Z");

    private static final DriveFolder NOTES = new DriveFolder("a1", "Notes");

    @Autowired
    private WatchedFolderRepository watchedFolderRepository;

    @Autowired
    private DriveConnectionRepository driveConnectionRepository;

    @Autowired
    private UserRepository userRepository;

    private UUID aConnection(String email) {
        UUID owner = userRepository
                .save(User.register(new Email(email), "empreinte"))
                .getId();
        return driveConnectionRepository
                .save(DriveConnection.connect(owner, "compte@gmail.com", new RefreshToken("1//jeton"), WATCHED_AT))
                .getId();
    }

    @Test
    void names_the_folder_itself_when_two_watches_of_it_race() {
        UUID connectionId = aConnection("dossier-double@exemple.fr");
        watchedFolderRepository.save(WatchedFolder.watch(connectionId, NOTES, WATCHED_AT));

        assertThatExceptionOfType(FolderAlreadyCoveredException.class)
                .isThrownBy(() -> watchedFolderRepository.save(WatchedFolder.watch(connectionId, NOTES, WATCHED_AT)))
                .withMessageContaining("Notes");
    }

    @Test
    void lets_a_broken_foreign_key_through_rather_than_naming_a_covering_folder() {
        WatchedFolder orphan = WatchedFolder.watch(UUID.randomUUID(), NOTES, WATCHED_AT);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> watchedFolderRepository.save(orphan))
                .satisfies(violation -> assertThat(violation).isNotInstanceOf(FolderAlreadyCoveredException.class));
    }
}
