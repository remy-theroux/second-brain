package xyz.sterenn.secondbrain.users.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

class UserTest {

    @Test
    void nait_non_verifie() {
        assertThat(User.register(new Email("alice@example.com"), "empreinte").isVerified()).isFalse();
    }

    @Test
    void devient_verifie_quand_son_adresse_est_confirmee() {
        User user = User.register(new Email("alice@example.com"), "empreinte");

        user.verify();

        assertThat(user.isVerified()).isTrue();
    }
}
