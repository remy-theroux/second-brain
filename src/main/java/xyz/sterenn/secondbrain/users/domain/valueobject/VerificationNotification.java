package xyz.sterenn.secondbrain.users.domain.valueobject;

import java.util.UUID;

/**
 * Invitation à vérifier l'adresse email d'un compte fraîchement créé.
 *
 * <p>Elle porte la donnée métier — qui, quel compte, quel jeton — et rien de la forme :
 * l'URL absolue, le sujet et le corps sont construits par l'adapter, qui seul connaît son
 * canal.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer le jeton, même règle que
 * {@code RegisterUser} pour le mot de passe.
 *
 * @param recipient adresse à notifier
 * @param accountId compte concerné, repris tel quel dans le lien
 * @param rawToken  jeton en clair, dont seule l'empreinte est stockée
 */
public record VerificationNotification(Email recipient, UUID accountId, RawVerificationToken rawToken)
        implements Notification {

    @Override
    public String toString() {
        return "VerificationNotification[recipient=" + recipient + ", accountId=" + accountId + ", rawToken=***]";
    }
}
