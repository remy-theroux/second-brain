package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceCatalogueTest {

    private static final UUID RAPPORT = UUID.randomUUID();
    private static final UUID NOTES = UUID.randomUUID();

    private static SourceCandidate extrait(UUID document, int position) {
        return new SourceCandidate(document, "rapport.pdf", position, "Introduction", "texte " + position);
    }

    @Test
    void part_vide() {
        assertThat(SourceCatalogue.empty().sources()).isEmpty();
        assertThat(SourceCatalogue.empty().contains(1)).isFalse();
    }

    @Test
    void numerote_les_extraits_a_partir_de_un_dans_l_ordre_recu() {
        Absorption absorption = SourceCatalogue.empty().absorb(List.of(extrait(RAPPORT, 0), extrait(RAPPORT, 1)));

        assertThat(absorption.nouveaux()).extracting(Source::number).containsExactly(1, 2);
        assertThat(absorption.dejaVus()).isZero();
        assertThat(absorption.catalogue().sources()).hasSize(2);
    }

    @Test
    void rend_son_numero_d_origine_a_un_extrait_deja_vu() {
        SourceCatalogue premier = SourceCatalogue.empty()
                .absorb(List.of(extrait(RAPPORT, 0), extrait(RAPPORT, 1)))
                .catalogue();

        Absorption seconde = premier.absorb(List.of(extrait(RAPPORT, 1), extrait(NOTES, 0)));

        assertThat(seconde.nouveaux()).extracting(Source::number).containsExactly(3);
        assertThat(seconde.dejaVus()).isEqualTo(1);
        assertThat(seconde.catalogue().sources()).extracting(Source::number).containsExactly(1, 2, 3);
    }

    @Test
    void distingue_deux_extraits_par_leur_document_autant_que_par_leur_position() {
        Absorption absorption = SourceCatalogue.empty().absorb(List.of(extrait(RAPPORT, 0), extrait(NOTES, 0)));

        assertThat(absorption.nouveaux()).hasSize(2);
    }

    @Test
    void n_apprend_rien_d_une_recherche_qui_ne_ramene_que_du_deja_vu() {
        SourceCatalogue premier =
                SourceCatalogue.empty().absorb(List.of(extrait(RAPPORT, 0))).catalogue();

        Absorption seconde = premier.absorb(List.of(extrait(RAPPORT, 0)));

        assertThat(seconde.nouveaux()).isEmpty();
        assertThat(seconde.dejaVus()).isEqualTo(1);
        assertThat(seconde.catalogue().sources()).hasSize(1);
    }

    @Test
    void ne_modifie_pas_le_catalogue_qu_il_absorbe() {
        SourceCatalogue depart = SourceCatalogue.empty();

        depart.absorb(List.of(extrait(RAPPORT, 0)));

        assertThat(depart.sources()).isEmpty();
    }

    @Test
    void ne_rend_que_les_sources_reellement_citees_et_connues() {
        SourceCatalogue catalogue = SourceCatalogue.empty()
                .absorb(List.of(extrait(RAPPORT, 0), extrait(RAPPORT, 1), extrait(RAPPORT, 2)))
                .catalogue();

        assertThat(catalogue.cited(List.of(3, 9, 1))).extracting(Source::number).containsExactly(3, 1);
    }
}
