package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.entity.DriveConnection;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAccessTokenRejectedException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.DriveAuthorizationRevokedException;
import xyz.sterenn.secondbrain.knowledge.domain.port.GoogleAccessTokens;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAccessToken;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

class DriveAccessTest {

    private static final Instant NOW = Instant.parse("2026-09-08T10:00:00Z");

    private final DriveConnection connection =
            DriveConnection.connect(UUID.randomUUID(), "alice@gmail.com", new RefreshToken("1//jeton-alice"), NOW);

    @Test
    void hands_the_call_the_token_of_the_connection() {
        StubAccessTokens accessTokens = new StubAccessTokens();
        DriveAccess driveAccess = new DriveAccess(accessTokens);

        assertThat(driveAccess.call(connection, DriveAccessToken::value)).isEqualTo("jeton-1");
        assertThat(accessTokens.invalidations).isZero();
    }

    @Test
    void replays_the_call_once_with_a_token_bought_after_the_rejection() {
        StubAccessTokens accessTokens = new StubAccessTokens();
        DriveAccess driveAccess = new DriveAccess(accessTokens);
        List<String> attempts = new ArrayList<>();

        String answer = driveAccess.call(connection, accessToken -> {
            attempts.add(accessToken.value());
            if (attempts.size() == 1) {
                throw new DriveAccessTokenRejectedException(new IllegalStateException("HTTP 401"));
            }
            return "les dossiers";
        });

        assertThat(answer).isEqualTo("les dossiers");
        assertThat(attempts).containsExactly("jeton-1", "jeton-2");
        assertThat(accessTokens.invalidations).isEqualTo(1);
    }

    @Test
    void lets_the_revocation_the_renewal_reveals_through() {
        StubAccessTokens accessTokens = new StubAccessTokens();
        accessTokens.revokeAfterTheFirstExchange();
        DriveAccess driveAccess = new DriveAccess(accessTokens);

        assertThatExceptionOfType(DriveAuthorizationRevokedException.class)
                .isThrownBy(() -> driveAccess.call(connection, accessToken -> {
                    throw new DriveAccessTokenRejectedException(new IllegalStateException("HTTP 401"));
                }));
        assertThat(accessTokens.invalidations).isEqualTo(1);
    }

    @Test
    void replays_once_and_never_twice() {
        StubAccessTokens accessTokens = new StubAccessTokens();
        DriveAccess driveAccess = new DriveAccess(accessTokens);
        List<String> attempts = new ArrayList<>();

        assertThatExceptionOfType(DriveAccessTokenRejectedException.class)
                .isThrownBy(() -> driveAccess.call(connection, accessToken -> {
                    attempts.add(accessToken.value());
                    throw new DriveAccessTokenRejectedException(new IllegalStateException("HTTP 401"));
                }));
        assertThat(attempts).containsExactly("jeton-1", "jeton-2");
        assertThat(accessTokens.invalidations).isEqualTo(1);
    }

    private static final class StubAccessTokens implements GoogleAccessTokens {

        private int exchanges = 0;
        private int invalidations = 0;
        private boolean revokedAfterTheFirstExchange = false;

        private void revokeAfterTheFirstExchange() {
            revokedAfterTheFirstExchange = true;
        }

        @Override
        public DriveAccessToken forConnection(DriveConnection connection) {
            if (revokedAfterTheFirstExchange && exchanges > 0) {
                throw new DriveAuthorizationRevokedException();
            }
            exchanges++;
            return new DriveAccessToken("jeton-" + exchanges, NOW.plus(Duration.ofHours(1)));
        }

        @Override
        public void invalidate(DriveConnection connection) {
            invalidations++;
        }
    }
}
