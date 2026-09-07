package xyz.sterenn.secondbrain.users.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.users.domain.valueobject.Email;

class UserTest {

    @Test
    void is_born_unverified() {
        assertThat(User.register(new Email("alice@example.com"), "empreinte").isVerified())
                .isFalse();
    }

    @Test
    void becomes_verified_when_its_address_is_confirmed() {
        User user = User.register(new Email("alice@example.com"), "empreinte");

        user.verify();

        assertThat(user.isVerified()).isTrue();
    }
}
