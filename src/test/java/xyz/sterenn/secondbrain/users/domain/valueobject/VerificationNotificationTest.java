package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationNotificationTest {

    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void carries_the_recipient_the_account_and_the_token() {
        RawVerificationToken token = RawVerificationToken.generate();

        VerificationNotification notification =
                new VerificationNotification(new Email("alice@example.com"), ACCOUNT, token);

        assertThat(notification.recipient()).isEqualTo(new Email("alice@example.com"));
        assertThat(notification.accountId()).isEqualTo(ACCOUNT);
        assertThat(notification.rawToken()).isEqualTo(token);
    }

    @Test
    void does_not_disclose_the_token_in_its_string_representation() {
        RawVerificationToken token = RawVerificationToken.generate();

        VerificationNotification notification =
                new VerificationNotification(new Email("alice@example.com"), ACCOUNT, token);

        assertThat(notification.toString()).doesNotContain(token.value());
    }

    @Test
    void is_indeed_a_notification() {
        Notification notification =
                new VerificationNotification(new Email("alice@example.com"), ACCOUNT, RawVerificationToken.generate());

        assertThat(notification.recipient()).isEqualTo(new Email("alice@example.com"));
    }
}
