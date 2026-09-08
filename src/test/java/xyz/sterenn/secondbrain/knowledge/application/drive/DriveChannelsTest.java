package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveChannel;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.DriveChannelRepository;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChanges;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleDriveChannels;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChangePage;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelSubscription;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

/**
 * The promise of the whole development: in development nothing of Google can reach us, so an
 * empty public address turns the channels off rather than failing the startup. Every port below
 * fails on the spot, which is what makes "no channel at all" mean "not a word to Google either".
 */
class DriveChannelsTest {

    @Test
    void opens_no_channel_and_asks_google_nothing_without_a_public_address() {
        assertThat(driveChannelsPublishedAt("").open(aConnection())).isEmpty();
    }

    /** A variable left empty in a Compose file is a handful of spaces as often as an empty string. */
    @Test
    void reads_a_blank_address_as_no_address_at_all() {
        assertThat(driveChannelsPublishedAt("   ").open(aConnection())).isEmpty();
    }

    private static DriveChannels driveChannelsPublishedAt(String webhookUrl) {
        return new DriveChannels(
                new RefusingDriveChannelRepository(),
                new RefusingGoogleDriveChannels(),
                new RefusingGoogleDriveChanges(),
                new DriveAccess(new RefusingGoogleAccessTokens()),
                Clock.systemUTC(),
                webhookUrl);
    }

    private static DriveConnection aConnection() {
        return DriveConnection.connect(
                UUID.randomUUID(), "alice@gmail.com", new RefreshToken("1//jeton"), Instant.now());
    }

    private static AssertionError called(String what) {
        return new AssertionError(what + " was called, when no channel was to be opened at all");
    }

    private static final class RefusingGoogleAccessTokens implements GoogleAccessTokens {

        @Override
        public DriveAccessToken forConnection(DriveConnection connection) {
            throw called("The access token of the connection");
        }

        @Override
        public void invalidate(DriveConnection connection) {
            throw called("The purge of the access token");
        }
    }

    private static final class RefusingGoogleDriveChannels implements GoogleDriveChannels {

        @Override
        public DriveChannelSubscription watch(
                DriveAccessToken accessToken,
                String channelId,
                DriveChannelToken token,
                String address,
                String pageToken,
                Duration lifetime) {
            throw called("changes.watch");
        }

        @Override
        public void stop(DriveAccessToken accessToken, String channelId, String resourceId) {
            throw called("channels.stop");
        }
    }

    private static final class RefusingGoogleDriveChanges implements GoogleDriveChanges {

        @Override
        public String startPageToken(DriveAccessToken accessToken) {
            throw called("changes.getStartPageToken");
        }

        @Override
        public DriveChangePage changesSince(DriveAccessToken accessToken, String pageToken) {
            throw called("changes.list");
        }
    }

    private static final class RefusingDriveChannelRepository implements DriveChannelRepository {

        @Override
        public DriveChannel save(DriveChannel driveChannel) {
            throw called("The channel repository");
        }

        @Override
        public Optional<DriveChannel> findByChannelId(String channelId) {
            throw called("The channel repository");
        }

        @Override
        public Optional<DriveChannel> findByConnectionId(UUID connectionId) {
            throw called("The channel repository");
        }

        @Override
        public void delete(DriveChannel driveChannel) {
            throw called("The channel repository");
        }
    }
}
