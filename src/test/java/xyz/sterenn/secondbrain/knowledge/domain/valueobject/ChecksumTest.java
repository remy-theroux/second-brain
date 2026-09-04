package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ChecksumTest {

    @Test
    void calcule_l_empreinte_sha_256_d_un_contenu_connu() {
        // Empreinte de référence de "abc", celle de la spécification FIPS 180-4.
        assertThat(Checksum.of("abc".getBytes(StandardCharsets.UTF_8)).value())
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void rend_la_meme_empreinte_pour_le_meme_contenu() {
        byte[] contenu = "Le même contenu, déposé deux fois.".getBytes(StandardCharsets.UTF_8);

        assertThat(Checksum.of(contenu)).isEqualTo(Checksum.of(contenu.clone()));
    }

    @Test
    void rend_une_empreinte_differente_pour_un_contenu_different() {
        assertThat(Checksum.of("premier".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(Checksum.of("second".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void accepte_un_contenu_vide() {
        assertThat(Checksum.of(new byte[0]).value()).hasSize(Checksum.LENGTH);
    }

    @Test
    void normalise_une_empreinte_ecrite_en_majuscules() {
        String majuscules = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";

        assertThat(new Checksum(majuscules)).isEqualTo(Checksum.of("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void refuse_une_empreinte_trop_courte() {
        assertThatThrownBy(() -> new Checksum("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_une_empreinte_non_hexadecimale() {
        assertThatThrownBy(() -> new Checksum("z".repeat(Checksum.LENGTH)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refuse_une_empreinte_vide() {
        assertThatThrownBy(() -> new Checksum("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
