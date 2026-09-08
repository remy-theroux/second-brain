package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    @Test
    void hides_its_value_in_toString() {
        RefreshToken token = new RefreshToken("1//04-un-vrai-jeton-de-rafraichissement");

        assertThat(token.toString()).doesNotContain("1//04-un-vrai-jeton-de-rafraichissement");
    }

    @Test
    void refuses_a_blank_value() {
        assertThatThrownBy(() -> new RefreshToken("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RefreshToken(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
