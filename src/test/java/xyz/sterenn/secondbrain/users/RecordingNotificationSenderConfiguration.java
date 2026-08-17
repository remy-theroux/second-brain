package xyz.sterenn.secondbrain.users;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Remplace le canal email par un enregistreur en mémoire. Les tests vérifient ainsi le
 * <em>port</em> et non l'adapter, et peuvent relire le jeton en clair pour enchaîner sur
 * la route de vérification — exactement ce que ferait l'utilisateur depuis sa boîte mail.
 *
 * <p>Le bean est partagé par tout le contexte : appeler {@link RecordingNotificationSender#clear()}
 * en {@code @BeforeEach}, le rollback de la transaction de test ne le vide pas.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordingNotificationSenderConfiguration {

    @Bean
    @Primary
    public RecordingNotificationSender recordingNotificationSender() {
        return new RecordingNotificationSender();
    }

    public static class RecordingNotificationSender implements NotificationSender {

        private final List<Notification> envoyees = new CopyOnWriteArrayList<>();

        @Override
        public void send(Notification notification) {
            envoyees.add(notification);
        }

        public List<VerificationNotification> verifications() {
            return envoyees.stream()
                .filter(VerificationNotification.class::isInstance)
                .map(VerificationNotification.class::cast)
                .toList();
        }

        public VerificationNotification derniere() {
            List<VerificationNotification> verifications = verifications();
            if (verifications.isEmpty()) {
                throw new IllegalStateException("Aucune notification de vérification enregistrée");
            }
            return verifications.getLast();
        }

        public void clear() {
            envoyees.clear();
        }
    }
}
