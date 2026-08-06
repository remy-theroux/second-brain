package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

/**
 * Vérification d'une adresse email à partir du lien reçu par notification.
 *
 * <p>Les champs sont des {@code String} bruts, tels qu'ils arrivent dans l'URL : c'est le
 * handler qui les interprète. Un identifiant illisible est un refus métier, pas une
 * erreur de conversion.
 *
 * <p>{@link #toString()} est redéfini pour ne jamais exposer {@code rawToken} : le jeton
 * en clair vaut mot de passe à usage unique tant qu'il n'est pas consommé.
 *
 * @param accountId identifiant du compte, tel que reçu
 * @param rawToken  jeton de vérification en clair
 */
public record VerifyAccount(String accountId, String rawToken) implements Command {

    @Override
    public String toString() {
        return "VerifyAccount[accountId=" + accountId + ", rawToken=***]";
    }
}
