package xyz.sterenn.secondbrain.users.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AccessTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {

    // Le profil dev journalise le corps des réponses en DEBUG, ce qui passe par ici : le
    // jeton est un porteur d'identité, il ne doit apparaître dans aucun log.
    @Override
    public String toString() {
        return "AccessTokenResponse[accessToken=***, tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
