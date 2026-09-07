package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessTokenTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");

    @Test
    void rejects_a_blank_value() {
        assertThatThrownBy(() -> new AccessToken("  ", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_missing_expiration() {
        assertThatThrownBy(() -> new AccessToken("eyJ", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void counts_the_remaining_seconds() {
        AccessToken token = new AccessToken("eyJ", NOW.plusSeconds(3600));

        assertThat(token.expiresIn(NOW)).isEqualTo(3600L);
    }

    @Test
    void never_counts_negative_seconds() {
        AccessToken token = new AccessToken("eyJ", NOW.minusSeconds(10));

        assertThat(token.expiresIn(NOW)).isZero();
    }

    @Test
    void does_not_disclose_its_value_in_its_string_representation() {
        AccessToken token = new AccessToken("eyJ.secret.abc", NOW);

        assertThat(token.toString()).doesNotContain("eyJ.secret.abc");
    }
}
