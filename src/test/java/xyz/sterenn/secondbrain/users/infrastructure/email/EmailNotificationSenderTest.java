package xyz.sterenn.secondbrain.users.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

class EmailNotificationSenderTest {

    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Null JavaMailSender: buildMessage never calls it, only its result is tested.
    private final EmailNotificationSender sender =
            new EmailNotificationSender(null, "http://localhost:8080", "no-reply@second-brain.localhost");

    private SimpleMailMessage message(RawVerificationToken token) {
        return sender.buildMessage(new VerificationNotification(new Email("alice@example.com"), ACCOUNT, token));
    }

    @Test
    void addresses_the_message_to_the_notification_recipient() {
        SimpleMailMessage message = message(RawVerificationToken.generate());

        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@second-brain.localhost");
    }

    @Test
    void announces_the_verification_in_the_subject() {
        assertThat(message(RawVerificationToken.generate()).getSubject()).isEqualTo("Vérifiez votre adresse email");
    }

    @Test
    void builds_the_absolute_verification_link() {
        RawVerificationToken token = RawVerificationToken.generate();

        assertThat(message(token).getText())
                .contains("http://localhost:8080/verification"
                        + "?compte=11111111-1111-1111-1111-111111111111"
                        + "&jeton=" + token.value());
    }

    @Test
    void announces_the_validity_period_of_the_link() {
        assertThat(message(RawVerificationToken.generate()).getText()).contains("24 heures");
    }
}
