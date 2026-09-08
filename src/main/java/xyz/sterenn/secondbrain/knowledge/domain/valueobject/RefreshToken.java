package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

public record RefreshToken(String value) {

    public RefreshToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("The Google refresh token is required");
        }
    }

    /** Masks the value: it grants a lasting access to a Drive, it must not leak into a log. */
    @Override
    public String toString() {
        return "RefreshToken[value=***]";
    }
}
