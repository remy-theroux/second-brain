package xyz.sterenn.secondbrain.users.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corps de réponse d'une délivrance de jeton, forme de RFC 6749 §5.1.
 *
 * <p>Le protocole impose du {@code snake_case} ; {@code @JsonProperty} le produit sans le
 * laisser contaminer les noms Java. (Jackson 3 a changé de groupe Maven pour le databind,
 * mais ses annotations vivent toujours dans {@code com.fasterxml.jackson.annotation}.)
 */
public record AccessTokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("token_type") String tokenType,
    @JsonProperty("expires_in") long expiresIn
) {
}
