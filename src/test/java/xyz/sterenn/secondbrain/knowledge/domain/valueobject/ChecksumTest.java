package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChecksumTest {

    @Test
    void computes_the_sha_256_checksum_of_a_known_content() {
        // Reference checksum of "abc", the one from the FIPS 180-4 specification.
        assertThat(Checksum.of("abc".getBytes(StandardCharsets.UTF_8)).value())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void returns_the_same_checksum_for_the_same_content() {
        byte[] content = "Le même contenu, déposé deux fois.".getBytes(StandardCharsets.UTF_8);

        assertThat(Checksum.of(content)).isEqualTo(Checksum.of(content.clone()));
    }

    @Test
    void returns_a_different_checksum_for_a_different_content() {
        assertThat(Checksum.of("premier".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(Checksum.of("second".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void accepts_an_empty_content() {
        assertThat(Checksum.of(new byte[0]).value()).hasSize(Checksum.LENGTH);
    }

    @Test
    void normalises_a_checksum_written_in_uppercase() {
        String uppercase = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";

        assertThat(new Checksum(uppercase)).isEqualTo(Checksum.of("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejects_a_checksum_that_is_too_short() {
        assertThatThrownBy(() -> new Checksum("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_non_hexadecimal_checksum() {
        assertThatThrownBy(() -> new Checksum("z".repeat(Checksum.LENGTH)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_blank_checksum() {
        assertThatThrownBy(() -> new Checksum("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
