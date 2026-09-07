package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegisterUserTest {

    @Test
    void never_exposes_the_password_in_its_string_representation() {
        String text = new RegisterUser("alice@example.com", "chevalpile42").toString();

        assertThat(text).doesNotContain("chevalpile42");
        assertThat(text).contains("alice@example.com");
    }
}
