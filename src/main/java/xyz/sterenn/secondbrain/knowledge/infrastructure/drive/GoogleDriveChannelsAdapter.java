package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.GoogleDriveUnavailableException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelSubscription;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;

class GoogleDriveChannelsAdapter implements GoogleDriveChannels {

    static final String WATCH_ENDPOINT = "https://www.googleapis.com/drive/v3/changes/watch";

    static final String STOP_ENDPOINT = "https://www.googleapis.com/drive/v3/channels/stop";

    static final String WEB_HOOK = "web_hook";

    /** Seconds, as a string, and the only way to ask for more than the hour Google grants by default. */
    static final String TIME_TO_LIVE = "ttl";

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveChannelsAdapter.class);

    private final RestClient restClient;

    GoogleDriveChannelsAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Override
    public DriveChannelSubscription watch(
            DriveAccessToken accessToken,
            String channelId,
            DriveChannelToken token,
            String address,
            String pageToken,
            Duration lifetime) {
        GoogleChannelResponse answer;
        try {
            answer = restClient
                    .post()
                    .uri(watchUri(pageToken))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "id",
                            channelId,
                            "type",
                            WEB_HOOK,
                            "address",
                            address,
                            "token",
                            token.value(),
                            "params",
                            Map.of(TIME_TO_LIVE, String.valueOf(lifetime.toSeconds()))))
                    .retrieve()
                    .body(GoogleChannelResponse.class);
        } catch (RestClientResponseException refusal) {
            LOG.error("Google refused a channel subscription: {}", describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused a channel subscription: {}", describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
        if (answer == null || GoogleFiles.isBlank(answer.resourceId())) {
            LOG.error("Google answered a channel with no resource identifier: it could never be stopped");
            throw new GoogleDriveUnavailableException();
        }
        return new DriveChannelSubscription(answer.resourceId(), expirationOf(answer));
    }

    @Override
    public void stop(DriveAccessToken accessToken, String channelId, String resourceId) {
        try {
            restClient
                    .post()
                    .uri(URI.create(STOP_ENDPOINT))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.value())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("id", channelId, "resourceId", resourceId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException refusal) {
            // A channel Google no longer knows is one that is closed: insisting would keep a row
            // alive that designates nothing.
            if (refusal.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                LOG.info("Google no longer knows the channel {}: it is already closed", channelId);
                return;
            }
            LOG.error("Google refused to stop the channel {}: {}", channelId, describe(refusal));
            throw translate(refusal);
        } catch (RestClientException failure) {
            LOG.error("Google refused to stop the channel {}: {}", channelId, describe(failure));
            throw new GoogleDriveUnavailableException(failure);
        }
    }

    /**
     * Seven days is a maximum, not a promise: Google may hand back a shorter deadline without
     * warning, so the one it hands back is the one that counts. An answer without a deadline is a
     * channel whose renewal could never be planned — better an outage than a subscription that
     * silently dies.
     */
    private static Instant expirationOf(GoogleChannelResponse answer) {
        if (GoogleFiles.isBlank(answer.expiration())) {
            LOG.error("Google answered a channel with no expiration: its renewal could never be planned");
            throw new GoogleDriveUnavailableException();
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(answer.expiration().trim()));
        } catch (NumberFormatException unreadable) {
            LOG.error("Google answered a channel expiration that is not a number of milliseconds");
            throw new GoogleDriveUnavailableException(unreadable);
        }
    }

    private static URI watchUri(String pageToken) {
        return UriComponentsBuilder.fromUriString(WATCH_ENDPOINT)
                .queryParam("pageToken", pageToken)
                .build()
                .encode()
                .toUri();
    }

    private static RuntimeException translate(RestClientResponseException refusal) {
        if (refusal.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
            return new DriveAccessTokenRejectedException(refusal);
        }
        return new GoogleDriveUnavailableException(refusal);
    }

    /** Status and type, never {@code getMessage()}, which would echo the response body into the log. */
    private static String describe(RestClientException failure) {
        if (failure instanceof RestClientResponseException refusal) {
            return failure.getClass().getSimpleName() + " HTTP "
                    + refusal.getStatusCode().value();
        }
        return failure.getClass().getSimpleName();
    }

    /** {@code expiration} is a number of milliseconds since the epoch, handed back as a string. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GoogleChannelResponse(String id, String resourceId, String expiration) {}
}
