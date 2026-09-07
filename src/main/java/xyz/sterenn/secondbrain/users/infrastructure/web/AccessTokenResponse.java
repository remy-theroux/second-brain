package xyz.sterenn.secondbrain.users.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {

    // The dev profile logs response bodies at DEBUG, which goes through here: the token
    // bears an identity, it must not appear in any log.
    @Override
    public String toString() {
        return "AccessTokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
