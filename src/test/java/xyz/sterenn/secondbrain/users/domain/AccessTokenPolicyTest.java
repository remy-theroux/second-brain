package xyz.sterenn.secondbrain.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccessTokenPolicyTest {

    @Test
    void expires_the_token_one_hour_after_it_was_issued() {
        Instant issuedAt = Instant.parse("2026-08-17T10:00:00Z");

        assertThat(AccessTokenPolicy.expiresAt(issuedAt)).isEqualTo(Instant.parse("2026-08-17T11:00:00Z"));
    }

    @Test
    void announces_a_one_hour_lifetime() {
        assertThat(AccessTokenPolicy.LIFETIME).isEqualTo(Duration.ofHours(1));
    }
}
