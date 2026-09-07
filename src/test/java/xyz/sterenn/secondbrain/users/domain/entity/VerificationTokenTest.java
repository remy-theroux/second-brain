package xyz.sterenn.secondbrain.users.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.exception.AlreadyUsedVerificationLinkException;
import xyz.sterenn.secondbrain.users.domain.exception.ExpiredVerificationLinkException;

class VerificationTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-06T10:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private VerificationToken issued() {
        return VerificationToken.issue(ACCOUNT, "empreinte", ISSUED_AT);
    }

    @Test
    void is_born_valid_and_unconsumed() {
        VerificationToken token = issued();

        assertThat(token.getUserId()).isEqualTo(ACCOUNT);
        assertThat(token.getTokenHash()).isEqualTo("empreinte");
        assertThat(token.isConsumed()).isFalse();
        assertThat(token.isExpired(ISSUED_AT)).isFalse();
    }

    @Test
    void expires_twenty_four_hours_after_it_was_issued() {
        VerificationToken token = issued();

        assertThat(token.getExpiresAt()).isEqualTo(ISSUED_AT.plus(VerificationToken.VALIDITY));
        assertThat(token.isExpired(ISSUED_AT.plus(Duration.ofHours(23)))).isFalse();
        assertThat(token.isExpired(ISSUED_AT.plus(Duration.ofHours(25)))).isTrue();
    }

    @Test
    void marks_the_consumption_instant() {
        VerificationToken token = issued();
        Instant click = ISSUED_AT.plus(Duration.ofMinutes(5));

        token.consume(click);

        assertThat(token.isConsumed()).isTrue();
        assertThat(token.getConsumedAt()).isEqualTo(click);
    }

    @Test
    void rejects_a_second_consumption() {
        VerificationToken token = issued();
        token.consume(ISSUED_AT.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> token.consume(ISSUED_AT.plus(Duration.ofMinutes(10))))
                .isInstanceOf(AlreadyUsedVerificationLinkException.class);
    }

    @Test
    void rejects_the_consumption_of_an_expired_token() {
        VerificationToken token = issued();

        assertThatThrownBy(() -> token.consume(ISSUED_AT.plus(Duration.ofHours(25))))
                .isInstanceOf(ExpiredVerificationLinkException.class);
        assertThat(token.isConsumed()).isFalse();
    }

    @Test
    void reports_the_double_use_first_when_the_token_is_also_expired() {
        VerificationToken token = issued();
        token.consume(ISSUED_AT.plus(Duration.ofMinutes(5)));

        assertThatThrownBy(() -> token.consume(ISSUED_AT.plus(Duration.ofHours(25))))
                .isInstanceOf(AlreadyUsedVerificationLinkException.class);
    }
}
