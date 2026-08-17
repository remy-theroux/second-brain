package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationNotificationTest {

    private static final UUID COMPTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void porte_le_destinataire_le_compte_et_le_jeton() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        VerificationNotification notification =
            new VerificationNotification(new Email("alice@example.com"), COMPTE, jeton);

        assertThat(notification.recipient()).isEqualTo(new Email("alice@example.com"));
        assertThat(notification.accountId()).isEqualTo(COMPTE);
        assertThat(notification.rawToken()).isEqualTo(jeton);
    }

    @Test
    void ne_divulgue_pas_le_jeton_dans_sa_representation_textuelle() {
        RawVerificationToken jeton = RawVerificationToken.generate();

        VerificationNotification notification =
            new VerificationNotification(new Email("alice@example.com"), COMPTE, jeton);

        assertThat(notification.toString()).doesNotContain(jeton.value());
    }

    @Test
    void est_bien_une_notification() {
        Notification notification =
            new VerificationNotification(new Email("alice@example.com"), COMPTE,
                RawVerificationToken.generate());

        assertThat(notification.recipient()).isEqualTo(new Email("alice@example.com"));
    }
}
