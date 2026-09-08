package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

/** A round trip to Google before every Drive call would double the latency of a browsing. */
class CachingGoogleAccessTokens implements GoogleAccessTokens {

    // Keyed by owner and not by connection id: an account holds at most one connection, and an
    // unpersisted one has no id yet.
    private final Map<UUID, CachedToken> tokens = new ConcurrentHashMap<>();

    private final GoogleAccessTokens exchange;
    private final Clock clock;

    CachingGoogleAccessTokens(GoogleAccessTokens exchange, Clock clock) {
        this.exchange = exchange;
        this.clock = clock;
    }

    @Override
    public DriveAccessToken forConnection(DriveConnection connection) {
        Instant now = clock.instant();
        CachedToken cached = tokens.get(connection.getOwnerId());
        if (cached != null && cached.stillAnswersFor(connection.getRefreshToken(), now)) {
            return cached.token();
        }
        DriveAccessToken exchanged = exchange.forConnection(connection);
        tokens.put(connection.getOwnerId(), new CachedToken(connection.getRefreshToken(), exchanged));
        return exchanged;
    }

    private record CachedToken(RefreshToken source, DriveAccessToken token) {

        /** A reconnection replaces the refresh token in place: the token it bought is stale. */
        private boolean stillAnswersFor(RefreshToken refreshToken, Instant now) {
            return source.equals(refreshToken) && token.isUsableAt(now);
        }
    }
}
