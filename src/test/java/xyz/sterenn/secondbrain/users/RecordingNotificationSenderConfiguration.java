package xyz.sterenn.secondbrain.users;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import xyz.sterenn.secondbrain.users.domain.port.NotificationSender;
import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

// Le bean est partagé par tout le contexte et le rollback de la transaction de test ne le
// vide pas : appeler RecordingNotificationSender.clear() en @BeforeEach.
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
