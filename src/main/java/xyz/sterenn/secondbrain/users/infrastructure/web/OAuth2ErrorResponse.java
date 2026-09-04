package xyz.sterenn.secondbrain.users.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

// Forme imposée par RFC 6749 §5.2, propre à /api/token : les autres routes suivent
// ValidationErrorResponse.
public record OAuth2ErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription) {

    public static OAuth2ErrorResponse invalidRequest(String description) {
        return new OAuth2ErrorResponse("invalid_request", description);
    }

    public static OAuth2ErrorResponse unsupportedGrantType(String description) {
        return new OAuth2ErrorResponse("unsupported_grant_type", description);
    }

    public static OAuth2ErrorResponse invalidGrant(String description) {
        return new OAuth2ErrorResponse("invalid_grant", description);
    }
}
