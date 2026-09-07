package xyz.sterenn.secondbrain.users.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void accepts_a_twelve_character_password() {
        assertThat(PasswordPolicy.isAcceptable("chevalpile42")).isTrue();
    }

    @Test
    void rejects_a_too_short_password() {
        assertThat(PasswordPolicy.isAcceptable("chevalpile4")).isFalse();
    }

    @Test
    void accepts_a_password_at_the_maximum_length() {
        // 10 + 118 = 128 characters
        assertThat(PasswordPolicy.isAcceptable("chevalpile" + "9".repeat(118))).isTrue();
    }

    @Test
    void rejects_a_too_long_password() {
        // 10 + 119 = 129 characters
        assertThat(PasswordPolicy.isAcceptable("chevalpile" + "9".repeat(119))).isFalse();
    }

    @Test
    void rejects_a_password_from_the_blocklist() {
        assertThat(PasswordPolicy.isAcceptable("motdepasse12")).isFalse();
    }

    @Test
    void rejects_a_password_from_the_blocklist_whatever_its_case() {
        assertThat(PasswordPolicy.isAcceptable("MotDePasse12")).isFalse();
    }

    @Test
    void rejects_a_null_password() {
        assertThat(PasswordPolicy.isAcceptable(null)).isFalse();
    }
}
