package xyz.sterenn.secondbrain.users.application.command;

import xyz.sterenn.secondbrain.shared.bus.Command;

public record RegisterUser(String email, String rawPassword) implements Command {

    /** Masque {@code rawPassword} : ni log ni message d'échec d'assertion ne doit le rendre en clair. */
    @Override
    public String toString() {
        return "RegisterUser[email=" + email + ", rawPassword=***]";
    }
}
