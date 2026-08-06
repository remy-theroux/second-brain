package xyz.sterenn.secondbrain.users.domain.port;

import xyz.sterenn.secondbrain.users.domain.valueobject.Notification;

/**
 * Port sortant vers le canal de notification. Le domaine ignore lequel est utilisé —
 * email aujourd'hui, autre chose demain.
 */
public interface NotificationSender {

    void send(Notification notification);
}
