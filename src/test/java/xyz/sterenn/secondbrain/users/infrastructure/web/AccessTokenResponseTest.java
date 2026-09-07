package xyz.sterenn.secondbrain.users.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessTokenResponseTest {

    @Test
    void does_not_disclose_its_value_in_its_string_representation() {
        AccessTokenResponse response = new AccessTokenResponse("eyJ.secret.abc", "Bearer", 3600L);

        assertThat(response.toString()).doesNotContain("eyJ.secret.abc");
    }
}
