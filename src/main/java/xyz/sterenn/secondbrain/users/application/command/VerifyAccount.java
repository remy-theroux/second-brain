package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

public record VerifyAccount(String accountId, String rawToken) implements Command {

    /** Masque {@code rawToken} : tant qu'il n'est pas consommé, il vaut mot de passe à usage unique. */
    @Override
    public String toString() {
        return "VerifyAccount[accountId=" + accountId + ", rawToken=***]";
    }
}
