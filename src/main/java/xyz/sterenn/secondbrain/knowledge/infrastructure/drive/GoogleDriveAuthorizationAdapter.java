package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveAuthorization;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveGrant;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

class GoogleDriveAuthorizationAdapter implements GoogleDriveAuthorization {

    static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";

    static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    static final String ABOUT_ENDPOINT = "https://www.googleapis.com/drive/v3/about";

    /** Reading, and nothing else: this context never writes into a Drive. */
    static final String SCOPE = "https://www.googleapis.com/auth/drive.readonly";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveAuthorizationAdapter.class);

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    GoogleDriveAuthorizationAdapter(RestClient restClient, String clientId, String clientSecret, String redirectUri) {
        this.restClient = restClient;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    /**
     * {@code access_type=offline} and {@code prompt=consent} are not decorative: without the
     * first Google delivers no refresh token, without the second it stops delivering one to an
     * account that has already consented.
     */
    @Override
    public URI consentUrl(DriveAuthorizationState state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state.value())
                .build()
                .encode()
                .toUri();
    }

    @Override
    public DriveGrant exchange(String authorizationCode) {
        GoogleTokenResponse token = requestToken(authorizationCode);
        if (token == null
                || token.refreshToken() == null
                || token.refreshToken().isBlank()) {
            // Symptom of a forgotten access_type=offline or of an already granted consent, and a
            // connection without a refresh token dies at the next restart.
            LOG.error("Google delivered no refresh token: check access_type=offline and prompt=consent");
            throw new GoogleDriveUnavailableException();
        }
        return new DriveGrant(readGoogleAddress(token.accessToken()), new RefreshToken(token.refreshToken()));
    }

    private GoogleTokenResponse requestToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", authorizationCode);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        try {
            return restClient
                    .post()
                    .uri(TOKEN_ENDPOINT)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(GoogleTokenResponse.class);
        } catch (RestClientException failure) {
            // Never the response body in the log: it carries the refresh token.
            LOG.error("The Google token endpoint refused the exchange: {}", failure.getMessage());
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    /** The address comes from {@code drive.readonly} itself: no {@code userinfo.email} scope. */
    private String readGoogleAddress(String accessToken) {
        GoogleAboutResponse about;
        try {
            about = restClient
                    .get()
                    .uri(UriComponentsBuilder.fromUriString(ABOUT_ENDPOINT)
                            .queryParam("fields", "user")
                            .build()
                            .toUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(GoogleAboutResponse.class);
        } catch (RestClientException failure) {
            LOG.error("Google did not hand back the address of the authorizing account: {}", failure.getMessage());
            throw new GoogleDriveUnavailableException(failure);
        }
        if (about == null || about.user() == null || about.user().emailAddress() == null) {
            LOG.error("The Google about.get answer carries no address: {}", about);
            throw new GoogleDriveUnavailableException();
        }
        return about.user().emailAddress();
    }
}
