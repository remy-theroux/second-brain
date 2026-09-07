package xyz.sterenn.secondbrain.users.application.query;

import xyz.sterenn.secondbrain.shared.bus.Query;

public record AuthenticateUser(String email, String rawPassword) implements Query<AccessTokenView> {

    /** Masks {@code rawPassword}: neither a log nor an assertion failure message may reveal it. */
    @Override
    public String toString() {
        return "AuthenticateUser[email=" + email + ", rawPassword=***]";
    }
}
