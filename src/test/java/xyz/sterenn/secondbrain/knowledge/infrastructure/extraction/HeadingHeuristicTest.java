package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingHeuristicTest {

    private static final String CORPS = "Une ligne de corps de texte, ordinaire et bavarde.";

    @Test
    void prend_pour_le_corps_la_taille_qui_porte_le_plus_de_caracteres() {
        List<Section> sections = HeadingHeuristic.decouper(List.of(
                new TextLine("Rapport annuel", 18f),
                new TextLine(CORPS, 11f),
                new TextLine(CORPS, 11f),
                new TextLine(CORPS, 11f)));

        assertThat(sections).extracting(Section::heading).containsExactly("Rapport annuel");
    }

    @Test
    void range_les_tailles_de_titre_de_la_plus_grande_a_la_plus_petite() {
        List<Section> sections = HeadingHeuristic.decouper(List.of(
                new TextLine("Rapport annuel", 18f),
                new TextLine(CORPS, 11f),
                new TextLine("Premiere partie", 14f),
                new TextLine(CORPS, 11f),
                new TextLine("Seconde partie", 14f),
                new TextLine(CORPS, 11f)));

        assertThat(sections).extracting(Section::level).containsExactly(1, 2, 2);
    }

    @Test
    void garde_hors_section_ce_qui_precede_le_premier_titre() {
        List<Section> sections = HeadingHeuristic.decouper(
                List.of(new TextLine(CORPS, 11f), new TextLine("Un titre", 18f), new TextLine(CORPS, 11f)));

        assertThat(sections).first().satisfies(section -> {
            assertThat(section.heading()).isEmpty();
            assertThat(section.level()).isZero();
        });
    }

    @Test
    void ne_voit_aucun_titre_dans_un_document_d_une_seule_taille() {
        List<Section> sections = HeadingHeuristic.decouper(
                List.of(new TextLine(CORPS, 11f), new TextLine(CORPS, 11f), new TextLine(CORPS, 11f)));

        assertThat(sections).singleElement().satisfies(section -> assertThat(section.heading())
                .isEmpty());
    }

    @Test
    void ne_prend_pas_pour_un_titre_une_phrase_entiere_mise_en_avant() {
        String citation = "C".repeat(200);

        List<Section> sections = HeadingHeuristic.decouper(
                List.of(new TextLine(citation, 18f), new TextLine(CORPS, 11f), new TextLine(CORPS, 11f)));

        assertThat(sections).extracting(Section::heading).containsExactly("");
    }

    @Test
    void ne_rend_aucune_section_quand_il_n_y_a_aucune_ligne() {
        assertThat(HeadingHeuristic.decouper(List.of())).isEmpty();
    }

    @Test
    void borne_le_niveau_a_six_meme_avec_sept_tailles_de_titre() {
        List<TextLine> lignes = new ArrayList<>();
        for (int taille = 30; taille >= 12; taille -= 3) {
            lignes.add(new TextLine("Titre de " + taille, taille));
            lignes.add(new TextLine(CORPS, 10f));
        }
        lignes.add(new TextLine(CORPS.repeat(5), 10f));

        assertThat(HeadingHeuristic.decouper(lignes)).extracting(Section::level).allMatch(niveau -> niveau <= 6);
    }
}
