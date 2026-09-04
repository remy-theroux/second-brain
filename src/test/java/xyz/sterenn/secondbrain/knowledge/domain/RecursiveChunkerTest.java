package xyz.sterenn.secondbrain.knowledge.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class RecursiveChunkerTest {

    /** Un mot, un token. La doublure qui rend les frontières lisibles. */
    private static final TokenCounter UN_MOT_UN_TOKEN =
            texte -> texte == null || texte.isBlank() ? 0 : texte.strip().split("\\s+").length;

    private final RecursiveChunker chunker = new RecursiveChunker(UN_MOT_UN_TOKEN);

    @Test
    void refuse_un_texte_absent() {
        assertThatNullPointerException().isThrownBy(() -> chunker.chunk(null));
    }

    @Test
    void un_document_plus_court_qu_un_extrait_donne_un_seul_extrait() {
        ExtractedText texte = ExtractedText.untitled(paragraphe(1, 10));

        assertThat(chunker.chunk(texte)).hasSize(1);
    }

    @Test
    void un_document_court_mais_titre_donne_un_extrait_par_section() {
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Première section", 1, paragraphe(1, 4)),
                TextBlock.of("Deuxième section", 1, paragraphe(5, 4)),
                TextBlock.of("Troisième section", 1, paragraphe(9, 4))));

        assertThat(chunker.chunk(texte)).hasSize(3);
    }

    @Test
    void une_section_sous_le_plafond_donne_un_extrait_meme_au_dessus_de_la_cible() {
        ExtractedText texte = ExtractedText.untitled(paragraphe(1, 70));

        assertThat(chunker.chunk(texte)).hasSize(1);
    }

    @Test
    void aucun_extrait_ne_depasse_le_plafond() {
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> assertThat(UN_MOT_UN_TOKEN.count(extrait.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void aucun_extrait_ne_commence_ni_ne_finit_au_milieu_d_une_phrase() {
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        assertThat(extraits).allSatisfy(extrait -> {
            assertThat(extrait.text()).endsWith(".");
            assertThat(extrait.text()).startsWith("Phrase numero ");
        });
    }

    @Test
    void deux_extraits_consecutifs_d_une_section_se_recouvrent() {
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        String premierePhraseDuSecond = extraits.get(1).text().split("(?<=\\.)\\s+")[0];
        assertThat(extraits.get(0).text()).contains(premierePhraseDuSecond);
    }

    @Test
    void le_recouvrement_ne_franchit_pas_une_frontiere_de_section() {
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Section A", 1, marque("Alpha", 200)), TextBlock.of("Section B", 1, marque("Beta", 200))));

        List<Chunk> extraits = chunker.chunk(texte);

        assertThat(extraits)
                .filteredOn(extrait -> extrait.heading().equals("Section B"))
                .allSatisfy(extrait -> assertThat(extrait.text()).doesNotContain("Alpha"));
    }

    @Test
    void chaque_extrait_porte_le_titre_de_la_section_dont_il_vient() {
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, paragraphe(1, 200)), TextBlock.of("Conclusion", 1, paragraphe(1, 5))));

        List<Chunk> extraits = chunker.chunk(texte);

        assertThat(extraits).extracting(Chunk::heading).contains("Introduction", "Conclusion");
        assertThat(extraits).last().satisfies(extrait -> assertThat(extrait.heading())
                .isEqualTo("Conclusion"));
    }

    @Test
    void decoupe_aux_paragraphes_avant_de_descendre_aux_phrases() {
        String corps = paragraphe(1, 30) + "\n\n" + paragraphe(31, 30) + "\n\n" + paragraphe(61, 30);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(corps));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits.get(0).text()).contains("\n\n");
    }

    @Test
    void un_paragraphe_geant_sans_ponctuation_est_coupe_faute_de_frontiere() {
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled("mot ".repeat(3000)));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> assertThat(UN_MOT_UN_TOKEN.count(extrait.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void le_recouvrement_cede_devant_le_plafond() {
        String phraseGeante = "Mot " + "mot ".repeat(798) + "final.";

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 60) + " " + phraseGeante));

        assertThat(extraits).hasSize(2);
        assertThat(extraits.get(1).text()).isEqualTo(phraseGeante.strip());
    }

    @Test
    void ne_publie_pas_un_extrait_que_le_suivant_reprend_en_entier() {
        ExtractedText texte =
                ExtractedText.untitled("Introduction." + "\n\n" + paragraphe(1, 70) + "\n\n" + paragraphe(71, 70));

        List<Chunk> extraits = chunker.chunk(texte);

        for (int index = 0; index < extraits.size() - 1; index++) {
            assertThat(extraits.get(index + 1).text())
                    .doesNotContain(extraits.get(index).text());
        }
    }

    @Test
    void ne_perd_aucune_phrase_du_document() {
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        for (int numero = 1; numero <= 200; numero++) {
            String attendue = phrase(numero);
            assertThat(extraits)
                    .anySatisfy(extrait -> assertThat(extrait.text()).contains(attendue));
        }
    }

    private static String phrase(int numero) {
        return "Phrase numero " + numero + " avec quelques mots pour occuper la place.";
    }

    private static String paragraphe(int premiere, int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(index -> phrase(premiere + index))
                .collect(Collectors.joining(" "));
    }

    private static String marque(String marque, int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(index -> marque + " numero " + index + " avec quelques mots pour occuper la place.")
                .collect(Collectors.joining(" "));
    }
}
