package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

class CachingGoogleAccessTokensTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    private static final Duration GOOGLE_LIFETIME = Duration.ofHours(1);

    private final AdjustableClock clock = new AdjustableClock(NOW);

    private final RecordingExchange exchange = new RecordingExchange(clock);

    private final CachingGoogleAccessTokens accessTokens = new CachingGoogleAccessTokens(exchange, clock);

    @Test
    void exchanges_once_and_reuses_the_token_until_it_expires() {
        DriveConnection connection = connectionOf("alice@gmail.com", "1//jeton-alice");

        DriveAccessToken first = accessTokens.forConnection(connection);
        clock.advance(Duration.ofMinutes(10));
        DriveAccessToken second = accessTokens.forConnection(connection);

        assertThat(second).isEqualTo(first);
        assertThat(exchange.exchanges()).hasSize(1);
    }

    @Test
    void exchanges_again_once_the_token_has_expired() {
        DriveConnection connection = connectionOf("alice@gmail.com", "1//jeton-alice");

        DriveAccessToken first = accessTokens.forConnection(connection);
        clock.advance(GOOGLE_LIFETIME.plusMinutes(1));
        DriveAccessToken second = accessTokens.forConnection(connection);

        assertThat(second).isNotEqualTo(first);
        assertThat(exchange.exchanges()).hasSize(2);
    }

    @Test
    void exchanges_again_shortly_before_expiry() {
        DriveConnection connection = connectionOf("alice@gmail.com", "1//jeton-alice");

        DriveAccessToken first = accessTokens.forConnection(connection);
        clock.advance(GOOGLE_LIFETIME.minusSeconds(10));
        DriveAccessToken second = accessTokens.forConnection(connection);

        assertThat(second).isNotEqualTo(first);
        assertThat(exchange.exchanges()).hasSize(2);
    }

    @Test
    void keeps_one_token_per_connection() {
        DriveConnection alice = connectionOf("alice@gmail.com", "1//jeton-alice");
        DriveConnection bob = connectionOf("bob@gmail.com", "1//jeton-bob");

        DriveAccessToken aliceToken = accessTokens.forConnection(alice);
        DriveAccessToken bobToken = accessTokens.forConnection(bob);

        assertThat(accessTokens.forConnection(alice)).isEqualTo(aliceToken);
        assertThat(accessTokens.forConnection(bob)).isEqualTo(bobToken);
        assertThat(bobToken).isNotEqualTo(aliceToken);
        assertThat(exchange.exchanges()).hasSize(2);
    }

    @Test
    void exchanges_again_once_the_kept_token_has_been_invalidated() {
        DriveConnection connection = connectionOf("alice@gmail.com", "1//jeton-alice");

        DriveAccessToken first = accessTokens.forConnection(connection);
        accessTokens.invalidate(connection);
        DriveAccessToken second = accessTokens.forConnection(connection);

        assertThat(second).isNotEqualTo(first);
        assertThat(exchange.exchanges()).hasSize(2);
    }

    @Test
    void invalidates_the_token_of_one_connection_only() {
        DriveConnection alice = connectionOf("alice@gmail.com", "1//jeton-alice");
        DriveConnection bob = connectionOf("bob@gmail.com", "1//jeton-bob");
        DriveAccessToken aliceToken = accessTokens.forConnection(alice);
        DriveAccessToken bobToken = accessTokens.forConnection(bob);

        accessTokens.invalidate(alice);

        assertThat(accessTokens.forConnection(alice)).isNotEqualTo(aliceToken);
        assertThat(accessTokens.forConnection(bob)).isEqualTo(bobToken);
    }

    @Test
    void exchanges_again_once_a_reconnection_has_changed_the_refresh_token() {
        DriveConnection connection = connectionOf("alice@gmail.com", "1//jeton-alice");

        DriveAccessToken first = accessTokens.forConnection(connection);
        connection.refresh("alice-pro@gmail.com", new RefreshToken("1//jeton-alice-pro"), clock.instant());
        DriveAccessToken second = accessTokens.forConnection(connection);

        assertThat(second).isNotEqualTo(first);
        assertThat(exchange.exchanges()).hasSize(2);
    }

    private static DriveConnection connectionOf(String googleEmail, String refreshToken) {
        return DriveConnection.connect(UUID.randomUUID(), googleEmail, new RefreshToken(refreshToken), NOW);
    }

    private static final class RecordingExchange implements GoogleAccessTokens {

        private final Clock clock;
        private final List<RefreshToken> exchanges = new ArrayList<>();

        private RecordingExchange(Clock clock) {
            this.clock = clock;
        }

        @Override
        public DriveAccessToken forConnection(DriveConnection connection) {
            exchanges.add(connection.getRefreshToken());
            return new DriveAccessToken(
                    "jeton-d-acces-" + exchanges.size(), clock.instant().plus(GOOGLE_LIFETIME));
        }

        @Override
        public void invalidate(DriveConnection connection) {}

        private List<RefreshToken> exchanges() {
            return List.copyOf(exchanges);
        }
    }

    private static final class AdjustableClock extends Clock {

        private Instant instant;

        private AdjustableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
