package xyz.sterenn.secondbrain.users.domain.valueobject;

/**
 * Message que le domaine décide d'adresser à un utilisateur. Notifier est une intention
 * métier ; le canal — email aujourd'hui — est un détail d'infrastructure.
 *
 * <p>L'interface est scellée : un adapter peut faire un {@code switch} exhaustif sur le
 * type de notification, et le compilateur lui imposera de traiter tout nouveau type le
 * jour où il en naîtra un. C'est ce qui garde le port générique sans le rendre flou.
 */
public sealed interface Notification permits VerificationNotification {

    Email recipient();
}
