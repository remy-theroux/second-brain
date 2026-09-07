package xyz.sterenn.secondbrain.users.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

// Shape imposed by RFC 6749 §5.2, specific to /api/token: the other routes follow
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
