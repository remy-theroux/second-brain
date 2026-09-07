package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.port.PasswordHasher;

class BCryptPasswordHasherTest {

    private final PasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void produces_a_hash_prefixed_with_the_algorithm() {
        String hash = hasher.hash("chevalpile42");

        assertThat(hash).startsWith("{bcrypt}$2a$");
        assertThat(hash).isNotEqualTo("chevalpile42");
    }

    @Test
    void recognises_the_original_password() {
        String hash = hasher.hash("chevalpile42");

        assertThat(hasher.matches("chevalpile42", hash)).isTrue();
        assertThat(hasher.matches("autrechose42", hash)).isFalse();
    }
}
