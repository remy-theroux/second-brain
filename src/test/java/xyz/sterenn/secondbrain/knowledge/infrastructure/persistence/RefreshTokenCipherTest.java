package xyz.sterenn.secondbrain.knowledge.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.RefreshToken;

class RefreshTokenCipherTest {

    private static final String KEY = "Y2xlLWRlLXRlc3QtZHJpdmUtc2Vjb25kLWJyYWluMzI=";

    private static final RefreshToken TOKEN = new RefreshToken("1//04-un-vrai-jeton-de-rafraichissement");

    private final RefreshTokenAttributeConverter converter = new RefreshTokenAttributeConverter(KEY);

    @Test
    void restores_the_token_it_enciphered() {
        String column = converter.convertToDatabaseColumn(TOKEN);

        assertThat(converter.convertToEntityAttribute(column)).isEqualTo(TOKEN);
    }

    @Test
    void never_writes_the_clear_token_to_the_column() {
        String column = converter.convertToDatabaseColumn(TOKEN);

        assertThat(column).doesNotContain(TOKEN.value());
        assertThat(new String(Base64.getDecoder().decode(column), StandardCharsets.ISO_8859_1))
                .doesNotContain(TOKEN.value());
    }

    @Test
    void enciphers_the_same_token_differently_twice() {
        assertThat(converter.convertToDatabaseColumn(TOKEN)).isNotEqualTo(converter.convertToDatabaseColumn(TOKEN));
    }

    @Test
    void refuses_a_ciphertext_that_was_tampered_with() {
        byte[] enciphered = Base64.getDecoder().decode(converter.convertToDatabaseColumn(TOKEN));
        enciphered[enciphered.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(enciphered);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keeps_a_null_on_both_sides() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void refuses_a_key_that_is_not_thirty_two_bytes() {
        String tooShort = Base64.getEncoder().encodeToString("trop-courte".getBytes());

        assertThatThrownBy(() -> new RefreshTokenAttributeConverter(tooShort))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("secondbrain.drive.token-encryption-key");
    }
}
