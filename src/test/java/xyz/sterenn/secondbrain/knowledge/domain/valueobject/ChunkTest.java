package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class ChunkTest {

    @Test
    void porte_le_titre_de_sa_section_et_son_corps() {
        Chunk extrait = new Chunk("Introduction", "Le corps de la section.");

        assertThat(extrait.heading()).isEqualTo("Introduction");
        assertThat(extrait.text()).isEqualTo("Le corps de la section.");
    }

    @Test
    void accepte_un_extrait_sans_titre() {
        assertThat(new Chunk("", "Un document sans titre.").heading()).isEmpty();
    }

    @Test
    void refuse_un_extrait_sans_corps() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Chunk("Introduction", "   "));
    }

    @Test
    void refuse_un_titre_absent() {
        // Vide, oui ; absent, non. Un consommateur qui préfixe ses extraits n'a pas à
        // distinguer deux formes d'absence — c'est déjà la règle de TextBlock.
        assertThatNullPointerException().isThrownBy(() -> new Chunk(null, "Un corps."));
    }

    @Test
    void deux_extraits_de_meme_contenu_sont_egaux() {
        assertThat(new Chunk("Titre", "Un corps.")).isEqualTo(new Chunk("Titre", "Un corps."));
    }

    @Test
    void se_presente_avec_son_document_et_sa_section() {
        Chunk extrait = new Chunk("Introduction", "Le corps de la section.");

        assertThat(extrait.contextualised("rapport.pdf"))
                .isEqualTo("Document: rapport.pdf — Section: Introduction\n\nLe corps de la section.");
    }

    @Test
    void se_presente_avec_son_seul_document_quand_la_section_n_a_pas_de_titre() {
        Chunk extrait = new Chunk("", "Le corps de la section.");

        assertThat(extrait.contextualised("rapport.pdf")).isEqualTo("Document: rapport.pdf\n\nLe corps de la section.");
    }

    @Test
    void refuse_de_se_presenter_sans_nom_de_document() {
        assertThatNullPointerException().isThrownBy(() -> new Chunk("Introduction", "Un corps.").contextualised(null));
    }
}
