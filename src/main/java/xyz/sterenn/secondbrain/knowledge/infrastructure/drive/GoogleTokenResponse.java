package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import com.fasterxml.jackson.annotation.JsonProperty;

record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("expires_in") Long expiresIn) {

    /** Masks both tokens: the default record toString would write them into any log line. */
    @Override
    public String toString() {
        return "GoogleTokenResponse[accessToken=***, refreshToken=***, expiresIn=" + expiresIn + "]";
    }
}
