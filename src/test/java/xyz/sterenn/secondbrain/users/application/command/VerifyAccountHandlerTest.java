package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class VerifyAccountHandlerTest {

    private static final String VALID_PASSWORD = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingNotificationSender notifications;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @BeforeEach
    void clears_the_notifications() {
        notifications.clear();
    }

    private VerificationNotification register(String email) {
        commandBus.dispatch(new RegisterUser(email, VALID_PASSWORD));
        return notifications.last();
    }

    private void ageTheToken(UUID accountId, Duration age) {
        // The handler writes the token with the real clock: ageing it in the database is
        // the only way to observe expiry without freezing the context clock.
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE users_verification_tokens "
                        + "SET expires_at = expires_at - CAST(? AS interval) WHERE user_id = ?",
                age.toHours() + " hours",
                accountId);
        // Without this clear, Hibernate's first-level cache would serve the entity back as
        // it was before the UPDATE.
        entityManager.clear();
    }

    @Test
    void verifies_the_account_when_the_token_matches() {
        VerificationNotification notification = register("alice@example.com");

        commandBus.dispatch(new VerifyAccount(
                notification.accountId().toString(), notification.rawToken().value()));

        assertThat(userRepository
                        .findById(notification.accountId())
                        .orElseThrow()
                        .isVerified())
                .isTrue();
    }

    @Test
    void rejects_a_token_that_does_not_match() {
        VerificationNotification notification = register("bob@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
                        new VerifyAccount(notification.accountId().toString(), "un-autre-jeton")))
                .isInstanceOf(InvalidVerificationLinkException.class);

        assertThat(userRepository
                        .findById(notification.accountId())
                        .orElseThrow()
                        .isVerified())
                .isFalse();
    }

    @Test
    void rejects_an_unknown_account() {
        VerificationNotification notification = register("carol@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
                        UUID.randomUUID().toString(), notification.rawToken().value())))
                .isInstanceOf(InvalidVerificationLinkException.class);
    }

    @Test
    void rejects_a_malformed_account_id() {
        VerificationNotification notification = register("dave@example.com");

        assertThatThrownBy(() -> commandBus.dispatch(
                        new VerifyAccount("pas-un-uuid", notification.rawToken().value())))
                .isInstanceOf(InvalidVerificationLinkException.class);
    }

    @Test
    void rejects_an_expired_token() {
        VerificationNotification notification = register("erin@example.com");
        ageTheToken(notification.accountId(), Duration.ofHours(25));

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
                        notification.accountId().toString(),
                        notification.rawToken().value())))
                .isInstanceOf(ExpiredVerificationLinkException.class);

        assertThat(userRepository
                        .findById(notification.accountId())
                        .orElseThrow()
                        .isVerified())
                .isFalse();
    }

    @Test
    void rejects_an_already_used_token() {
        VerificationNotification notification = register("frank@example.com");
        commandBus.dispatch(new VerifyAccount(
                notification.accountId().toString(), notification.rawToken().value()));

        assertThatThrownBy(() -> commandBus.dispatch(new VerifyAccount(
                        notification.accountId().toString(),
                        notification.rawToken().value())))
                .isInstanceOf(AlreadyUsedVerificationLinkException.class);

        assertThat(userRepository
                        .findById(notification.accountId())
                        .orElseThrow()
                        .isVerified())
                .isTrue();
    }
}
