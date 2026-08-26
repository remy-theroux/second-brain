package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class TextBlockTest {

    @Test
    void garde_le_titre_son_niveau_et_le_texte() {
        TextBlock bloc = TextBlock.of("Introduction", 1, "Le corps de la section.");

        assertThat(bloc.getHeading()).isEqualTo("Introduction");
        assertThat(bloc.getHeadingLevel()).isEqualTo(1);
        assertThat(bloc.getText()).isEqualTo("Le corps de la section.");
    }

    @Test
    void un_bloc_sans_titre_porte_le_niveau_zero() {
        TextBlock bloc = TextBlock.untitled("Tout le texte du document.");

        assertThat(bloc.getHeading()).isEmpty();
        assertThat(bloc.getHeadingLevel()).isZero();
    }

    @Test
    void ramene_le_niveau_a_zero_quand_le_titre_est_vide() {
        assertThat(TextBlock.of("   ", 3, "Du texte.").getHeadingLevel()).isZero();
    }

    @Test
    void refuse_un_niveau_hors_de_un_a_six_pour_un_titre_renseigne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TextBlock.of("Titre", 7, "Du texte."))
                .withMessageContaining("1 à 6");
    }

    @Test
    void refuse_un_bloc_dont_le_texte_est_vide_une_fois_normalise() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TextBlock.untitled("  \n\n \t "))
                .withMessageContaining("sans texte");
    }

    @Test
    void normalise_les_fins_de_ligne_et_les_espaces_de_fin() {
        TextBlock bloc = TextBlock.untitled("Première ligne   \r\nDeuxième ligne\r");

        assertThat(bloc.getText()).isEqualTo("Première ligne\nDeuxième ligne");
    }

    @Test
    void conserve_la_frontiere_de_paragraphe_mais_pas_la_mise_en_page() {
        TextBlock bloc = TextBlock.untitled("Paragraphe un.\n\n\n\n\nParagraphe deux.");

        assertThat(bloc.getText()).isEqualTo("Paragraphe un.\n\nParagraphe deux.");
    }

    @Test
    void efface_le_trait_d_union_conditionnel_qui_couperait_les_mots() {
        assertThat(TextBlock.untitled("consti\u00ADtution").getText()).isEqualTo("constitution");
    }

    @Test
    void ramene_l_espace_insecable_a_un_espace_ordinaire_sans_coller_les_mots() {
        assertThat(TextBlock.untitled("Article\u00A0premier : le texte.").getText())
                .isEqualTo("Article premier : le texte.");
    }

    @Test
    void efface_la_marque_d_ordre_des_octets_en_tete_de_fichier() {
        assertThat(TextBlock.untitled("\uFEFFPremière ligne du fichier.").getText())
                .isEqualTo("Première ligne du fichier.");
    }

    @Test
    void aplatit_les_blancs_d_un_titre_sur_une_seule_ligne() {
        TextBlock bloc = TextBlock.of("Chapitre\n premier   ", 1, "Du texte.");

        assertThat(bloc.getHeading()).isEqualTo("Chapitre premier");
    }

    @Test
    void tronque_un_titre_trop_long_plutot_que_de_le_refuser() {
        String tresLong = "T".repeat(TextBlock.MAX_HEADING_LENGTH + 42);

        assertThat(TextBlock.of(tresLong, 1, "Du texte.").getHeading()).hasSize(TextBlock.MAX_HEADING_LENGTH);
    }

    @Test
    void deux_blocs_de_meme_contenu_sont_egaux() {
        assertThat(TextBlock.of("Titre", 2, "Corps.")).isEqualTo(TextBlock.of("Titre", 2, "Corps."));
    }
}
