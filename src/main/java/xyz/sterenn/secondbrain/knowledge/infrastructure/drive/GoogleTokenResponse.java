package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import com.fasterxml.jackson.annotation.JsonProperty;

record GoogleTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken) {}
