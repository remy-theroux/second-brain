package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.util.UUID;

public record VerificationNotification(Email recipient, UUID accountId, RawVerificationToken rawToken)
        implements Notification {

    /** Masks {@code rawToken}: the clear text must appear neither in a log nor in a failure message. */
    @Override
    public String toString() {
        return "VerificationNotification[recipient=" + recipient + ", accountId=" + accountId + ", rawToken=***]";
    }
}
