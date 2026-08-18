package xyz.sterenn.secondbrain.users.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Corps de réponse d'un refus, forme de RFC 6749 §5.2. Les codes {@code error} sont ceux
 * du protocole ; {@code error_description} porte le message métier, affichable tel quel.
 */
public record OAuth2ErrorResponse(
    @JsonProperty("error") String error,
    @JsonProperty("error_description") String errorDescription
) {

    /** Requête mal formée : un paramètre obligatoire manque. */
    public static OAuth2ErrorResponse invalidRequest(String description) {
        return new OAuth2ErrorResponse("invalid_request", description);
    }

    /** Type d'autorisation non pris en charge par ce serveur. */
    public static OAuth2ErrorResponse unsupportedGrantType(String description) {
        return new OAuth2ErrorResponse("unsupported_grant_type", description);
    }

    /** Identifiants refusés, pour quelque raison métier que ce soit. */
    public static OAuth2ErrorResponse invalidGrant(String description) {
        return new OAuth2ErrorResponse("invalid_grant", description);
    }
}
