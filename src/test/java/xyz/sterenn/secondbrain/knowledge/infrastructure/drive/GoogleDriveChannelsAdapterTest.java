package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import xyz.sterenn.secondbrain.knowledge.domain.DriveChannelPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelSubscription;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;

class GoogleDriveChannelsAdapterTest {

    private static final DriveAccessToken ACCESS_TOKEN =
            new DriveAccessToken("ya29.jeton", Instant.now().plus(Duration.ofHours(1)));

    private static final DriveChannelToken CHANNEL_TOKEN = new DriveChannelToken("secret-du-canal");

    private static final Instant DEADLINE = Instant.parse("2026-09-15T10:00:00Z");

    private MockRestServiceServer server;
    private GoogleDriveChannelsAdapter adapter;

    @BeforeEach
    void wire_the_stub_server() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new GoogleDriveChannelsAdapter(builder);
    }

    /**
     * The lifetime is the point: a watch that carries no {@code params.ttl} gets one hour from
     * Google, whatever the seven days the policy means to have.
     */
    @Test
    void asks_google_for_the_address_the_token_and_the_lifetime_of_the_channel() {
        server.expect(requestTo(startsWith(GoogleDriveChannelsAdapter.WATCH_ENDPOINT)))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ya29.jeton"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("canal-1"))
                .andExpect(jsonPath("$.type").value(GoogleDriveChannelsAdapter.WEB_HOOK))
                .andExpect(jsonPath("$.address").value("https://second-brain.test/api/drive/notifications"))
                .andExpect(jsonPath("$.token").value(CHANNEL_TOKEN.value()))
                .andExpect(jsonPath("$.params.ttl").value("604800"))
                .andRespond(withSuccess(
                        "{\"resourceId\":\"r1\",\"expiration\":\"" + DEADLINE.toEpochMilli() + "\"}",
                        MediaType.APPLICATION_JSON));

        DriveChannelSubscription subscription = adapter.watch(
                ACCESS_TOKEN,
                "canal-1",
                CHANNEL_TOKEN,
                "https://second-brain.test/api/drive/notifications",
                "1789",
                DriveChannelPolicy.REQUESTED_LIFETIME);

        assertThat(subscription).isEqualTo(new DriveChannelSubscription("r1", DEADLINE));
        server.verify();
    }

    /** The position Drive demands travels in the query string, not in the body. */
    @Test
    void tells_google_where_the_feed_it_watches_starts() {
        server.expect(requestTo(GoogleDriveChannelsAdapter.WATCH_ENDPOINT + "?pageToken=1789"))
                .andRespond(withSuccess(
                        "{\"resourceId\":\"r1\",\"expiration\":\"" + DEADLINE.toEpochMilli() + "\"}",
                        MediaType.APPLICATION_JSON));

        adapter.watch(ACCESS_TOKEN, "canal-1", CHANNEL_TOKEN, "https://second-brain.test", "1789", Duration.ofDays(7));

        server.verify();
    }
}
