package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.entity.User;
import xyz.sterenn.secondbrain.users.domain.entity.VerificationToken;
import xyz.sterenn.secondbrain.users.domain.exception.EmailAlreadyUsedException;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidEmailException;
import xyz.sterenn.secondbrain.users.domain.exception.WeakPasswordException;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.port.UserRepository;
import xyz.sterenn.secondbrain.users.domain.port.VerificationTokenRepository;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class RegisterUserHandlerTest {

    private static final String VALID_PASSWORD = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private VerificationTokenRepository verificationTokenRepository;

    @Autowired
    private TokenHasher tokenHasher;

    @Autowired
    private RecordingNotificationSender notifications;

    @BeforeEach
    void clears_the_notifications() {
        notifications.clear();
    }

    @Test
    void creates_an_unverified_account_with_a_hashed_password() {
        commandBus.dispatch(new RegisterUser("alice@example.com", VALID_PASSWORD));

        User created =
                userRepository.findByEmail(new Email("alice@example.com")).orElseThrow();
        assertThat(created.getId()).isNotNull();
        assertThat(created.isVerified()).isFalse();
        assertThat(created.getPasswordHash()).isNotEqualTo(VALID_PASSWORD);
        assertThat(passwordHasher.matches(VALID_PASSWORD, created.getPasswordHash()))
                .isTrue();
    }

    @Test
    void normalises_the_email_before_storing_it() {
        commandBus.dispatch(new RegisterUser("  Bob@Example.COM  ", VALID_PASSWORD));

        assertThat(userRepository.existsByEmail(new Email("bob@example.com"))).isTrue();
    }

    @Test
    void rejects_an_already_used_email() {
        commandBus.dispatch(new RegisterUser("carol@example.com", VALID_PASSWORD));

        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("CAROL@Example.com", VALID_PASSWORD)))
                .isInstanceOf(EmailAlreadyUsedException.class)
                .hasMessageContaining("carol@example.com");
    }

    @Test
    void rejects_a_too_weak_password_without_creating_an_account() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("dave@example.com", "court")))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(userRepository.existsByEmail(new Email("dave@example.com"))).isFalse();
    }

    @Test
    void rejects_a_malformed_email() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("pas-un-email", VALID_PASSWORD)))
                .isInstanceOf(InvalidEmailException.class);
    }

    @Test
    void validates_the_password_before_the_email_uniqueness() {
        commandBus.dispatch(new RegisterUser("erin@example.com", VALID_PASSWORD));

        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("erin@example.com", "court")))
                .isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void notifies_the_new_account_of_its_verification() {
        commandBus.dispatch(new RegisterUser("frank@example.com", VALID_PASSWORD));

        VerificationNotification notification = notifications.last();
        assertThat(notification.recipient()).isEqualTo(new Email("frank@example.com"));
        assertThat(notification.rawToken().value()).isNotBlank();
    }

    @Test
    void issues_a_token_of_which_only_the_hash_is_stored() {
        commandBus.dispatch(new RegisterUser("grace@example.com", VALID_PASSWORD));

        VerificationNotification notification = notifications.last();
        VerificationToken token = verificationTokenRepository
                .findByUserId(notification.accountId())
                .orElseThrow();

        assertThat(token.getTokenHash()).doesNotContain(notification.rawToken().value());
        assertThat(tokenHasher.matches(notification.rawToken().value(), token.getTokenHash()))
                .isTrue();
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void addresses_the_notification_to_the_account_actually_created() {
        commandBus.dispatch(new RegisterUser("heidi@example.com", VALID_PASSWORD));

        VerificationNotification notification = notifications.last();
        assertThat(userRepository
                        .findByEmail(new Email("heidi@example.com"))
                        .orElseThrow()
                        .getId())
                .isEqualTo(notification.accountId());
    }

    @Test
    void does_not_notify_when_the_registration_is_rejected() {
        assertThatThrownBy(() -> commandBus.dispatch(new RegisterUser("ivan@example.com", "court")))
                .isInstanceOf(WeakPasswordException.class);

        assertThat(notifications.verifications()).isEmpty();
    }
}
