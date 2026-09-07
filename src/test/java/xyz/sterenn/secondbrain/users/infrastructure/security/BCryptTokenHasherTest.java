package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.port.TokenHasher;
import xyz.sterenn.secondbrain.users.domain.valueobject.RawVerificationToken;

class BCryptTokenHasherTest {

    private final TokenHasher hasher = new BCryptTokenHasher();

    @Test
    void never_returns_the_token_in_clear_text() {
        String token = RawVerificationToken.generate().value();

        assertThat(hasher.hash(token)).doesNotContain(token);
    }

    @Test
    void recognises_the_original_token() {
        String token = RawVerificationToken.generate().value();

        assertThat(hasher.matches(token, hasher.hash(token))).isTrue();
    }

    @Test
    void rejects_another_token() {
        String hash = hasher.hash(RawVerificationToken.generate().value());

        assertThat(hasher.matches(RawVerificationToken.generate().value(), hash))
                .isFalse();
    }

    @Test
    void produces_a_different_hash_on_each_call_for_the_same_token() {
        String token = RawVerificationToken.generate().value();

        assertThat(hasher.hash(token)).isNotEqualTo(hasher.hash(token));
    }
}
