package xyz.sterenn.secondbrain.users.application.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticateUserTest {

    @Test
    void does_not_disclose_the_password_in_its_string_representation() {
        AuthenticateUser query = new AuthenticateUser("alice@exemple.fr", "chevalpile42");

        assertThat(query.toString()).contains("alice@exemple.fr").doesNotContain("chevalpile42");
    }
}
