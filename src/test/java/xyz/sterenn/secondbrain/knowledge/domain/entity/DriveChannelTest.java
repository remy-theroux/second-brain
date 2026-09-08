package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.DriveChannelPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChannelToken;

class DriveChannelTest {

    private static final UUID CONNECTION = UUID.randomUUID();

    private static final Instant OPENED_AT = Instant.parse("2026-09-08T10:00:00Z");

    private static final Instant EXPIRES_AT = OPENED_AT.plus(Duration.ofDays(7));

    @Test
    void draws_a_token_that_differs_from_one_channel_to_the_next() {
        assertThat(anOpenChannel().getToken()).isNotEqualTo(anOpenChannel().getToken());
        assertThat(anOpenChannel().getChannelId()).isNotEqualTo(anOpenChannel().getChannelId());
    }

    @Test
    void accepts_the_token_it_was_opened_with() {
        DriveChannel channel = anOpenChannel();

        assertThat(channel.accepts(channel.getToken(), OPENED_AT.plus(Duration.ofDays(1))))
                .isTrue();
    }

    @Test
    void refuses_a_token_that_does_not_match() {
        DriveChannel channel = anOpenChannel();

        assertThat(channel.accepts(anOpenChannel().getToken(), OPENED_AT)).isFalse();
        assertThat(channel.accepts(null, OPENED_AT)).isFalse();
    }

    @Test
    void refuses_any_token_once_it_has_expired() {
        DriveChannel channel = anOpenChannel();

        assertThat(channel.accepts(channel.getToken(), EXPIRES_AT.plusSeconds(1)))
                .isFalse();
        assertThat(channel.accepts(channel.getToken(), EXPIRES_AT.minusSeconds(1)))
                .isTrue();
    }

    @Test
    void needs_a_renewal_before_its_deadline() {
        DriveChannel channel = anOpenChannel();
        Instant margin = EXPIRES_AT.minus(DriveChannelPolicy.RENEWAL_MARGIN);

        assertThat(channel.needsRenewal(margin.minusSeconds(1))).isFalse();
        assertThat(channel.needsRenewal(margin.plusSeconds(1))).isTrue();
        assertThat(channel.needsRenewal(EXPIRES_AT.plusSeconds(1))).isTrue();
    }

    /** Google hands back the resource and the deadline: until then the channel authenticates nothing. */
    @Test
    void authenticates_nothing_until_google_has_confirmed_it() {
        DriveChannel channel = DriveChannel.open(CONNECTION, OPENED_AT);

        assertThat(channel.accepts(channel.getToken(), OPENED_AT)).isFalse();
        assertThat(channel.needsRenewal(OPENED_AT)).isTrue();
    }

    @Test
    void refuses_a_subscription_without_the_resource_that_closes_it() {
        DriveChannel channel = DriveChannel.open(CONNECTION, OPENED_AT);

        assertThatThrownBy(() -> channel.subscribed("  ", EXPIRES_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> channel.subscribed("r1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DriveChannel anOpenChannel() {
        DriveChannel channel = DriveChannel.open(CONNECTION, OPENED_AT);
        channel.subscribed("ressource-" + UUID.randomUUID(), EXPIRES_AT);
        return channel;
    }

    /** Kept explicit so a token that stopped being masked shows up here rather than in a log. */
    @Test
    void never_prints_its_token() {
        DriveChannelToken token = anOpenChannel().getToken();

        assertThat(token.toString()).doesNotContain(token.value());
    }
}
