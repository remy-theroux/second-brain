package xyz.sterenn.secondbrain.users.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

class EmailNotificationSenderTest {

    private static final UUID COMPTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // JavaMailSender nul : buildMessage ne le sollicite jamais, seul son résultat est testé.
    private final EmailNotificationSender sender =
            new EmailNotificationSender(null, "http://localhost:8080", "no-reply@second-brain.localhost");

    private SimpleMailMessage message(RawVerificationToken jeton) {
        return sender.buildMessage(new VerificationNotification(new Email("alice@example.com"), COMPTE, jeton));
    }

    @Test
    void adresse_le_message_au_destinataire_de_la_notification() {
        SimpleMailMessage message = message(RawVerificationToken.generate());

        assertThat(message.getTo()).containsExactly("alice@example.com");
        assertThat(message.getFrom()).isEqualTo("no-reply@second-brain.localhost");
    }

    @Test
    void annonce_la_verification_dans_le_sujet() {
        assertThat(message(RawVerificationToken.generate()).getSubject()).isEqualTo("Vérifiez votre adresse email");
    }

    @Test
    void construit_le_lien_absolu_de_verification() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        assertThat(message(jeton).getText())
                .contains("http://localhost:8080/verification"
                        + "?compte=11111111-1111-1111-1111-111111111111"
                        + "&jeton=" + jeton.value());
    }

    @Test
    void annonce_la_duree_de_validite_du_lien() {
        assertThat(message(RawVerificationToken.generate()).getText()).contains("24 heures");
    }
}
