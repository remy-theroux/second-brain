package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalInt;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class CitationPolicyTest {

    private static final IntPredicate HUIT_EXTRAITS = numero -> numero >= 1 && numero <= 8;

    @Test
    void ne_voit_aucune_citation_dans_un_texte_qui_n_en_porte_pas() {
        assertThat(CitationPolicy.citations("Le délai est de quatorze jours.")).isEmpty();
    }

    @Test
    void rend_les_citations_dans_l_ordre_d_apparition_sans_repetition() {
        assertThat(CitationPolicy.citations("un [3] deux [1] trois [3] quatre [12]"))
                .containsExactly(3, 1, 12);
    }

    @Test
    void ignore_ce_qui_ressemble_a_une_citation_sans_en_etre_une() {
        assertThat(CitationPolicy.citations("[abc] [ 3 ] [] [3.5]")).isEmpty();
    }

    @Test
    void ignore_la_balise_d_extrait_qui_entoure_les_donnees() {
        assertThat(CitationPolicy.citations("<extrait numero=\"1\" document=\"a.pdf\">texte</extrait>"))
                .isEmpty();
    }

    @Test
    void situe_la_fin_de_la_premiere_citation_connue() {
        String texte = "Le délai est de quatorze jours [2] selon vos documents.";

        OptionalInt fin = CitationPolicy.endOfFirstValidCitation(texte, HUIT_EXTRAITS);

        assertThat(fin).hasValue(texte.indexOf("[2]") + "[2]".length());
    }

    @Test
    void ne_situe_rien_quand_la_seule_citation_est_inconnue() {
        assertThat(CitationPolicy.endOfFirstValidCitation("Le délai [9] est long.", HUIT_EXTRAITS))
                .isEmpty();
    }

    @Test
    void saute_une_citation_inconnue_pour_trouver_la_suivante() {
        String texte = "Faux [9] mais vrai [4] ensuite.";

        assertThat(CitationPolicy.endOfFirstValidCitation(texte, HUIT_EXTRAITS))
                .hasValue(texte.indexOf("[4]") + "[4]".length());
    }

    @Test
    void ne_situe_rien_dans_une_citation_encore_coupee_en_deux() {
        assertThat(CitationPolicy.endOfFirstValidCitation("Le délai [", HUIT_EXTRAITS))
                .isEmpty();
        assertThat(CitationPolicy.endOfFirstValidCitation("Le délai [2", HUIT_EXTRAITS))
                .isEmpty();
    }
}
