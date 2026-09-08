package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DriveAuthorizationStateTest {

    @Test
    void draws_a_state_that_differs_from_one_call_to_the_next() {
        assertThat(DriveAuthorizationState.random()).isNotEqualTo(DriveAuthorizationState.random());
    }

    @Test
    void draws_a_state_that_travels_in_a_url_without_escaping() {
        assertThat(DriveAuthorizationState.random().value()).matches("[A-Za-z0-9_-]+");
    }
}
