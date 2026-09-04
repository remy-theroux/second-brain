package xyz.sterenn.secondbrain.users.domain.valueobject;

public sealed interface Notification permits VerificationNotification {

    Email recipient();
}
