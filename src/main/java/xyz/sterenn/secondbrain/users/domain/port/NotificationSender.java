package xyz.sterenn.secondbrain.users.domain.port;

import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;

/** Achemine une notification décidée par le domaine, par un canal qu'il ignore. */
public interface NotificationSender {

    void send(Notification notification);
}
