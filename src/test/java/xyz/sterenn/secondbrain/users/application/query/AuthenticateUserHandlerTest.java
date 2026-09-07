package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Transactional;
import xyz.sterenn.secondbrain.TestcontainersConfiguration;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.AccountFixture;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidCredentialsException;
import xyz.sterenn.secondbrain.users.domain.exception.UnverifiedAccountException;

@Import({TestcontainersConfiguration.class, RecordingNotificationSenderConfiguration.class})
@SpringBootTest
@Transactional
class AuthenticateUserHandlerTest {

    private static final String PASSWORD = "chevalpile42";

    @Autowired
    private CommandBus commandBus;

    @Autowired
    private QueryBus queryBus;

    @Autowired
    private RecordingNotificationSender recordingNotificationSender;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void clears_the_recorded_notifications() {
        recordingNotificationSender.clear();
    }

    @Test
    void issues_a_token_to_the_verified_account_giving_the_right_password() {
        UUID accountId =
                AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        AccessTokenView view = queryBus.ask(new AuthenticateUser("alice@exemple.fr", PASSWORD));

        assertThat(view.expiresIn()).isEqualTo(3600L);
        assertThat(jwtDecoder.decode(view.value()).getSubject()).isEqualTo(accountId.toString());
    }

    @Test
    void accepts_an_email_typed_with_a_different_case() {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        AccessTokenView view = queryBus.ask(new AuthenticateUser("ALICE@Exemple.FR", PASSWORD));

        assertThat(view.value()).isNotBlank();
    }

    @Test
    void rejects_an_incorrect_password() {
        AccountFixture.registerVerified(commandBus, recordingNotificationSender, "alice@exemple.fr", PASSWORD);

        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("alice@exemple.fr", "chevalpile43")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejects_an_unknown_email() {
        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("inconnu@exemple.fr", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejects_a_malformed_email_as_wrong_credentials() {
        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("pas-un-email", PASSWORD)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejects_an_account_whose_address_is_not_verified() {
        AccountFixture.register(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);

        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("bob@exemple.fr", PASSWORD)))
                .isInstanceOf(UnverifiedAccountException.class)
                .hasMessageContaining("vérifié");
    }

    @Test
    void does_not_reveal_that_an_account_exists_to_whoever_ignores_the_password() {
        AccountFixture.register(commandBus, recordingNotificationSender, "bob@exemple.fr", PASSWORD);

        assertThatThrownBy(() -> queryBus.ask(new AuthenticateUser("bob@exemple.fr", "chevalpile43")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
