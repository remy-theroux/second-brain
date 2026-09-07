package xyz.sterenn.secondbrain.users.domain.port;

import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;

/** Delivers a notification decided by the domain, over a channel unknown to it. */
public interface NotificationSender {

    void send(Notification notification);
}
