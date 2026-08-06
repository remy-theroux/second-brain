package xyz.sterenn.secondbrain.users.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.PasswordHasher;

class BCryptPasswordHasherTest {

    private final PasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void produit_une_empreinte_prefixee_de_l_algorithme() {
        String empreinte = hasher.hash("chevalpile42");

        assertThat(empreinte).startsWith("{bcrypt}$2a$");
        assertThat(empreinte).isNotEqualTo("chevalpile42");
    }

    @Test
    void reconnait_le_mot_de_passe_d_origine() {
        String empreinte = hasher.hash("chevalpile42");

        assertThat(hasher.matches("chevalpile42", empreinte)).isTrue();
        assertThat(hasher.matches("autrechose42", empreinte)).isFalse();
    }
}
