package xyz.sterenn.secondbrain.users.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RawVerificationTokenTest {

    @Test
    void generates_a_non_blank_token() {
        assertThat(RawVerificationToken.generate().value()).isNotBlank();
    }

    @Test
    void generates_a_token_usable_as_is_in_a_url() {
        assertThat(RawVerificationToken.generate().value()).matches("^[A-Za-z0-9_-]+$");
    }

    @Test
    void generates_a_token_long_enough_not_to_be_guessed() {
        // 32 bytes encoded in base64 without padding give 43 characters.
        assertThat(RawVerificationToken.generate().value()).hasSize(43);
    }

    @Test
    void generates_a_different_token_on_each_call() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            tokens.add(RawVerificationToken.generate().value());
        }
        assertThat(tokens).hasSize(100);
    }

    @Test
    void rejects_a_blank_token() {
        assertThatThrownBy(() -> new RawVerificationToken("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void does_not_disclose_the_token_in_its_string_representation() {
        RawVerificationToken token = RawVerificationToken.generate();

        assertThat(token.toString()).doesNotContain(token.value());
    }
}
