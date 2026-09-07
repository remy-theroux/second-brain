package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccessTokenViewTest {

    @Test
    void does_not_disclose_its_value_in_its_string_representation() {
        AccessTokenView view = new AccessTokenView("eyJ.secret.abc", 3600L);

        assertThat(view.toString()).doesNotContain("eyJ.secret.abc");
    }
}
