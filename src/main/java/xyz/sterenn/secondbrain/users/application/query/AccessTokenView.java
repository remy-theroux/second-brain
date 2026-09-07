package xyz.sterenn.secondbrain.users.application.query;

public record AccessTokenView(String value, long expiresIn) {

    /** Masks the token: whoever holds it is that user. */
    @Override
    public String toString() {
        return "AccessTokenView[value=***, expiresIn=" + expiresIn + "]";
    }
}
