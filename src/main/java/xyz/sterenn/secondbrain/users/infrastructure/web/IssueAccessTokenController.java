package xyz.sterenn.secondbrain.users.infrastructure.web;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import xyz.sterenn.secondbrain.shared.bus.QueryBus;
import xyz.sterenn.secondbrain.users.application.query.AccessTokenView;
import xyz.sterenn.secondbrain.users.application.query.AuthenticateUser;
import xyz.sterenn.secondbrain.users.domain.exception.InvalidCredentialsException;
import xyz.sterenn.secondbrain.users.domain.exception.UnverifiedAccountException;

/**
 * Adapter entrant de la délivrance de jeton. Il a la <em>forme</em> du {@code password
 * grant} de RFC 6749 §4.3 sans serveur d'autorisation derrière : OAuth 2.1 a supprimé ce
 * type d'autorisation, et un client first-party n'a ni redirection ni consentement à gérer.
 *
 * <p>Les paramètres sont lus avec {@code defaultValue = ""}, comme dans
 * {@code VerifyAccountController} : un paramètre absent doit produire <em>notre</em> erreur
 * de protocole, pas le {@code 400} générique de Spring.
 *
 * <p>Aucune règle métier ici : le refus vient du handler, ce contrôleur choisit le code
 * d'erreur du protocole qui lui correspond.
 */
@RestController
public class IssueAccessTokenController {

    private static final String PASSWORD_GRANT_TYPE = "password";
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final QueryBus queryBus;

    public IssueAccessTokenController(QueryBus queryBus) {
        this.queryBus = queryBus;
    }

    @PostMapping(path = "/api/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Object> issueAccessToken(
            @RequestParam(name = "grant_type", defaultValue = "") String grantType,
            @RequestParam(name = "username", defaultValue = "") String username,
            @RequestParam(name = "password", defaultValue = "") String password) {
        if (grantType.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(OAuth2ErrorResponse.invalidRequest("Le paramètre grant_type est obligatoire."));
        }
        if (!PASSWORD_GRANT_TYPE.equals(grantType)) {
            return ResponseEntity.badRequest()
                    .body(OAuth2ErrorResponse.unsupportedGrantType(
                            "Seul le type d'autorisation « password » est accepté."));
        }
        if (username.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(OAuth2ErrorResponse.invalidRequest("Les paramètres username et password sont obligatoires."));
        }

        try {
            AccessTokenView accessToken = queryBus.ask(new AuthenticateUser(username, password));
            // no-store : RFC 6749 §5.1 interdit qu'une réponse portant un jeton soit mise en cache.
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(new AccessTokenResponse(accessToken.value(), BEARER_TOKEN_TYPE, accessToken.expiresIn()));
        } catch (InvalidCredentialsException | UnverifiedAccountException e) {
            return ResponseEntity.badRequest().body(OAuth2ErrorResponse.invalidGrant(e.getMessage()));
        }
    }
}
