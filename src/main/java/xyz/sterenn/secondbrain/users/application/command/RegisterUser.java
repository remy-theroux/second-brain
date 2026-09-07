package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

public record RegisterUser(String email, String rawPassword) implements Command {

    /** Masks {@code rawPassword}: neither a log nor an assertion failure message may reveal it. */
    @Override
    public String toString() {
        return "RegisterUser[email=" + email + ", rawPassword=***]";
    }
}
