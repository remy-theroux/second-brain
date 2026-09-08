package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;

class GoogleAccessTokensAdapter implements GoogleAccessTokens {

    static final String REVOKED_ERROR = "invalid_grant";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAccessTokensAdapter.class);

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    GoogleAccessTokensAdapter(RestClient restClient, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    @Override
    public DriveAccessToken forConnection(DriveConnection connection) {
        GoogleTokenResponse token = requestToken(connection);
        if (token == null
                || token.accessToken() == null
                || token.accessToken().isBlank()
                || token.expiresIn() == null) {
            LOG.error("The Google token endpoint answered a refresh without a usable access token");
            throw new GoogleDriveUnavailableException();
        }
        return new DriveAccessToken(token.accessToken(), clock.instant().plus(Duration.ofSeconds(token.expiresIn())));
    }

    private GoogleTokenResponse requestToken(DriveConnection connection) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("refresh_token", connection.getRefreshToken().value());
        form.add("grant_type", "refresh_token");
        try {
            return restClient
                    .post()
                    .uri(GoogleDriveAuthorizationAdapter.TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientResponseException refusal) {
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error(
                    "The Google token endpoint refused the refresh: {}",
                    failure.getClass().getSimpleName());
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    /**
     * {@code invalid_grant} is the only signal Google gives of an access withdrawn from the
     * account: a network failure or a 500 is an outage, not a revocation.
     */
    private static RuntimeException translate(RestClientResponseException refusal) {
        LOG.error(
                "The Google token endpoint refused the refresh: HTTP {}",
                refusal.getStatusCode().value());
        if (refusal.getStatusCode() == HttpStatus.BAD_REQUEST && REVOKED_ERROR.equals(errorOf(refusal))) {
            return new DriveAuthorizationRevokedException(refusal);
        }
        return new GoogleDriveUnavailableException(refusal);
    }

    private static String errorOf(RestClientResponseException refusal) {
        try {
            GoogleTokenErrorResponse body = refusal.getResponseBodyAs(GoogleTokenErrorResponse.class);
            return body == null ? null : body.error();
        } catch (RestClientException unreadableBody) {
            return null;
        }
    }
}
