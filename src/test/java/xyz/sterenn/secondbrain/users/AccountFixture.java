package xyz.sterenn.secondbrain.users;

import java.util.UUID;
import xyz.sterenn.secondbrain.shared.bus.CommandBus;
import xyz.sterenn.secondbrain.users.RecordingNotificationSenderConfiguration.RecordingNotificationSender;
import xyz.sterenn.secondbrain.users.application.command.RegisterUser;
import xyz.sterenn.secondbrain.users.application.command.VerifyAccount;
import xyz.sterenn.secondbrain.users.domain.valueobject.VerificationNotification;

public final class AccountFixture {

    private AccountFixture() {}

    public static UUID register(
            CommandBus commandBus,
            RecordingNotificationSender recordingNotificationSender,
            String email,
            String rawPassword) {
        commandBus.dispatch(new RegisterUser(email, rawPassword));
        return recordingNotificationSender.derniere().accountId();
    }

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
