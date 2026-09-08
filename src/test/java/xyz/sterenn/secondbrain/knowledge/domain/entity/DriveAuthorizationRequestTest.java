package xyz.sterenn.secondbrain.knowledge.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.DriveAuthorizationPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.InvalidDriveAuthorizationException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveAuthorizationState;

class DriveAuthorizationRequestTest {

    private static final Instant OPENED_AT = Instant.parse("2026-09-08T10:00:00Z");
    private static final UUID ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private DriveAuthorizationRequest opened() {
        return DriveAuthorizationRequest.open(ACCOUNT, DriveAuthorizationState.random(), OPENED_AT);
    }

    @Test
    void keeps_the_state_it_was_opened_with() {
        DriveAuthorizationState state = DriveAuthorizationState.random();

        assertThat(DriveAuthorizationRequest.open(ACCOUNT, state, OPENED_AT).getState())
                .isEqualTo(state);
    }

    @Test
    void accepts_a_state_that_matches_before_it_expires() {
        DriveAuthorizationRequest request = opened();
        Instant callback = OPENED_AT.plus(Duration.ofMinutes(2));

        request.consume(request.getState(), callback);

        assertThat(request.getOwnerId()).isEqualTo(ACCOUNT);
        assertThat(request.isConsumed()).isTrue();
        assertThat(request.getConsumedAt()).isEqualTo(callback);
    }

    @Test
    void refuses_a_state_that_does_not_match() {
        DriveAuthorizationRequest request = opened();

        assertThatThrownBy(() -> request.consume(DriveAuthorizationState.random(), OPENED_AT))
                .isInstanceOf(InvalidDriveAuthorizationException.class);
        assertThat(request.isConsumed()).isFalse();
    }

    @Test
    void refuses_a_request_older_than_the_policy() {
        DriveAuthorizationRequest request = opened();
        Instant tooLate = OPENED_AT.plus(DriveAuthorizationPolicy.VALIDITY).plusSeconds(1);

        assertThatThrownBy(() -> request.consume(request.getState(), tooLate))
                .isInstanceOf(InvalidDriveAuthorizationException.class);
        assertThat(request.isConsumed()).isFalse();
    }

    @Test
    void refuses_a_request_already_consumed() {
        DriveAuthorizationRequest request = opened();
        request.consume(request.getState(), OPENED_AT.plus(Duration.ofMinutes(1)));

        assertThatThrownBy(() -> request.consume(request.getState(), OPENED_AT.plus(Duration.ofMinutes(2))))
                .isInstanceOf(InvalidDriveAuthorizationException.class);
    }
}
