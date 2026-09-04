package xyz.sterenn.secondbrain.knowledge.application.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCandidate;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.SourceCatalogue;

class CitationBufferTest {

    private static SourceCatalogue troisExtraits() {
        UUID document = UUID.randomUUID();
        return SourceCatalogue.empty()
                .absorbe(List.of(
                        new SourceCandidate(document, "a.pdf", 0, "", "un"),
                        new SourceCandidate(document, "a.pdf", 1, "", "deux"),
                        new SourceCandidate(document, "a.pdf", 2, "", "trois")))
                .catalogue();
    }

    @Test
    void ne_laisse_rien_passer_tant_qu_aucune_citation_n_est_apparue() {
        List<String> sortis = new ArrayList<>();
        CitationBuffer tampon = new CitationBuffer(troisExtraits(), sortis::add);

        tampon.accepte("Canberra est ");
        tampon.accepte("la capitale de l'Australie.");

        assertThat(sortis).isEmpty();
        assertThat(tampon.aOuvert()).isFalse();
        assertThat(tampon.texte()).isEqualTo("Canberra est la capitale de l'Australie.");
    }

    @Test
    void ouvre_les_vannes_a_la_premiere_citation_et_rejoue_ce_qui_precede() {
        List<String> sortis = new ArrayList<>();
        CitationBuffer tampon = new CitationBuffer(troisExtraits(), sortis::add);

        tampon.accepte("Quatorze jours ");
        tampon.accepte("[2].");
        tampon.accepte(" Et ensuite [1].");

        assertThat(sortis).containsExactly("Quatorze jours [2].", " Et ensuite [1].");
        assertThat(tampon.aOuvert()).isTrue();
    }

    @Test
    void reconnait_une_citation_coupee_entre_deux_fragments() {
        List<String> sortis = new ArrayList<>();
        CitationBuffer tampon = new CitationBuffer(troisExtraits(), sortis::add);

        tampon.accepte("Quatorze jours [");
        assertThat(sortis).isEmpty();
        tampon.accepte("3].");

        assertThat(sortis).containsExactly("Quatorze jours [3].");
    }

    @Test
    void n_ouvre_pas_sur_une_citation_hors_catalogue() {
        List<String> sortis = new ArrayList<>();
        CitationBuffer tampon = new CitationBuffer(troisExtraits(), sortis::add);

        tampon.accepte("Canberra [9] est la capitale.");

        assertThat(sortis).isEmpty();
        assertThat(tampon.aOuvert()).isFalse();
    }

    @Test
    void laisse_remonter_l_echec_de_la_sortie() {
        CitationBuffer tampon = new CitationBuffer(troisExtraits(), fragment -> {
            throw new IllegalStateException("le client a fermé");
        });

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> tampon.accepte("Quatorze jours [1]."))
                .withMessageContaining("le client a fermé");
    }
}
