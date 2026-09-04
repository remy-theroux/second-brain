package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.util.UUID;

public record VerificationNotification(Email recipient, UUID accountId, RawVerificationToken rawToken)
        implements Notification {

    /** Masque {@code rawToken} : le clair ne doit apparaître ni dans un log ni dans un message d'échec. */
    @Override
    public String toString() {
        return "VerificationNotification[recipient=" + recipient + ", accountId=" + accountId + ", rawToken=***]";
    }
}
