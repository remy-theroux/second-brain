package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

public record VerifyAccount(String accountId, String rawToken) implements Command {

    /** Masks {@code rawToken}: until it is consumed, it is worth a single-use password. */
    @Override
    public String toString() {
        return "VerifyAccount[accountId=" + accountId + ", rawToken=***]";
    }
}
