package xyz.sterenn.secondbrain.users.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VerifyAccountTest {

    @Test
    void does_not_disclose_the_token_in_its_string_representation() {
        VerifyAccount command = new VerifyAccount("11111111-1111-1111-1111-111111111111", "un-jeton-tres-secret");

        assertThat(command.toString())
                .doesNotContain("un-jeton-tres-secret")
                .contains("11111111-1111-1111-1111-111111111111");
    }
}
