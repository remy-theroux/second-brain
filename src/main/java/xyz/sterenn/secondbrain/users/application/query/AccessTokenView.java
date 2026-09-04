package xyz.sterenn.secondbrain.users.application.query;

public record AccessTokenView(String value, long expiresIn) {

    /** Masque le jeton : quiconque le détient est cet utilisateur. */
    @Override
    public String toString() {
        return "AccessTokenView[value=***, expiresIn=" + expiresIn + "]";
    }
}
