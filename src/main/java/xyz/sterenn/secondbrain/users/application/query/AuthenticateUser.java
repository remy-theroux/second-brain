package xyz.sterenn.secondbrain.users.application.query;

import xyz.sterenn.secondbrain.shared.bus.Query;

public record AuthenticateUser(String email, String rawPassword) implements Query<AccessTokenView> {

    /** Masque {@code rawPassword} : ni log ni message d'échec d'assertion ne doit le rendre en clair. */
    @Override
    public String toString() {
        return "AuthenticateUser[email=" + email + ", rawPassword=***]";
    }
}
