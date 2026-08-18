package xyz.sterenn.secondbrain.users;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.application.command.VerifyAccount;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

/**
 * Crée des comptes dans les tests <em>par le chemin réel</em> : inscription par le bus,
 * puis lecture du jeton dans l'enregistreur de notifications et vérification par la
 * commande dédiée. Aucun test ne bascule {@code verified} à la main — un raccourci qui
 * contournerait le domaine finirait par cacher une régression du domaine.
 *
 * <p>L'enregistreur est un bean partagé par tout le contexte et n'est pas vidé par le
 * rollback : appeler {@code clear()} en {@code @BeforeEach} avant d'utiliser cette classe.
 */
public final class AccountFixture {

    private AccountFixture() {
        // classe utilitaire
    }

    /** Crée un compte non vérifié et rend son identifiant. */
    public static UUID register(
            CommandBus commandBus,
            RecordingNotificationSender recordingNotificationSender,
            String email,
            String rawPassword) {
        commandBus.dispatch(new RegisterUser(email, rawPassword));
        return recordingNotificationSender.derniere().accountId();
    }

    /** Crée un compte puis suit son lien de vérification, comme le ferait l'utilisateur. */
    public static UUID registerVerified(
            CommandBus commandBus,
            RecordingNotificationSender recordingNotificationSender,
            String email,
            String rawPassword) {
        commandBus.dispatch(new RegisterUser(email, rawPassword));
        VerificationNotification notification = recordingNotificationSender.derniere();
        commandBus.dispatch(new VerifyAccount(
                notification.accountId().toString(), notification.rawToken().value()));
        return notification.accountId();
    }
}
