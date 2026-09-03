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

/**
 * Le découpage, mesuré avec un compteur d'essai où <strong>un mot vaut un token</strong>.
 *
 * <p>C'est toute la raison d'être du port {@code TokenCounter} : avec cette toise, une phrase
 * de ce test pèse exactement dix tokens, la cible de 600 tombe sur la soixantième et le
 * recouvrement de 90 sur les neuf dernières. Les frontières se lisent, au lieu d'être des
 * nombres qu'il faudrait croire sur parole.
 *
 * <p>Aucun Spring : la classe testée est du domaine pur, et son unique dépendance est une
 * lambda.
 */
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
        // Le troisième scénario du ticket : une centaine de mots, sans titre.
        ExtractedText texte = ExtractedText.untitled(paragraphe(1, 10));

        assertThat(chunker.chunk(texte)).hasSize(1);
    }

    @Test
    void une_section_sous_le_plafond_donne_un_extrait_meme_au_dessus_de_la_cible() {
        // 70 phrases : 700 tokens, au-dessus de la cible (600) mais sous le plafond (800).
        // On ne coupe pas un bloc déjà valide pour se rapprocher de la cible.
        ExtractedText texte = ExtractedText.untitled(paragraphe(1, 70));

        assertThat(chunker.chunk(texte)).hasSize(1);
    }

    @Test
    void aucun_extrait_ne_depasse_le_plafond() {
        // Le premier scénario du ticket. 200 phrases : 2000 tokens.
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
        // Le deuxième scénario du ticket : une section plus longue qu'un extrait.
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        String premierePhraseDuSecond = extraits.get(1).text().split("(?<=\\.)\\s+")[0];
        assertThat(extraits.get(0).text()).contains(premierePhraseDuSecond);
    }

    @Test
    void le_recouvrement_ne_franchit_pas_une_frontiere_de_section() {
        // Deux titres, deux sections : un recouvrement à cheval ferait mentir le préfixe de
        // l'extrait suivant, qui annoncerait une section dont il ne contient pas le début.
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Section A", 1, marque("Alpha", 200)), TextBlock.of("Section B", 1, marque("Beta", 200))));

        List<Chunk> extraits = chunker.chunk(texte);

        assertThat(extraits)
                .filteredOn(extrait -> extrait.heading().equals("Section B"))
                .allSatisfy(extrait -> assertThat(extrait.text()).doesNotContain("Alpha"));
    }

    @Test
    void chaque_extrait_porte_le_titre_de_la_section_dont_il_vient() {
        // Le quatrième scénario du ticket, côté domaine : lu isolément, un extrait dit de
        // quelle section il provient. Le document, lui, est dit par la ligne qui le range.
        ExtractedText texte = new ExtractedText(List.of(
                TextBlock.of("Introduction", 1, paragraphe(1, 200)), TextBlock.of("Conclusion", 1, paragraphe(1, 5))));

        List<Chunk> extraits = chunker.chunk(texte);

        assertThat(extraits).extracting(Chunk::heading).contains("Introduction", "Conclusion");
        assertThat(extraits).last().satisfies(extrait -> assertThat(extrait.heading())
                .isEqualTo("Conclusion"));
    }

    @Test
    void decoupe_aux_paragraphes_avant_de_descendre_aux_phrases() {
        // Trois paragraphes de 300 tokens : 900 au total, donc la section se découpe. La
        // frontière de paragraphe survit dans les extraits — c'est le double saut de ligne
        // que TextBlock.normalise garantit.
        String corps = paragraphe(1, 30) + "\n\n" + paragraphe(31, 30) + "\n\n" + paragraphe(61, 30);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(corps));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits.get(0).text()).contains("\n\n");
    }

    @Test
    void un_paragraphe_geant_sans_ponctuation_est_coupe_faute_de_frontiere() {
        // Le scénario imposé par le transport : un texte qui n'offre aucune frontière ne peut
        // pas en imposer une. C'est le seul endroit où la promesse « jamais au milieu d'une
        // phrase » cède, et elle y est forcée.
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled("mot ".repeat(3000)));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> assertThat(UN_MOT_UN_TOKEN.count(extrait.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void le_recouvrement_cede_devant_le_plafond() {
        // 60 phrases (600 tokens), puis une seule phrase de 800 tokens. Reprendre la moindre
        // phrase la ferait passer au-dessus du plafond : le recouvrement est abandonné en
        // entier, et l'extrait est la phrase géante, seule. Sans cette règle, ce serait
        // l'unique extrait hors plafond de tout l'algorithme.
        String phraseGeante = "Mot " + "mot ".repeat(798) + "final.";

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 60) + " " + phraseGeante));

        assertThat(extraits).hasSize(2);
        assertThat(extraits.get(1).text()).isEqualTo(phraseGeante.strip());
    }

    @Test
    void ne_publie_pas_un_extrait_que_le_suivant_reprend_en_entier() {
        // Une courte accroche suivie de longs paragraphes : sans garde, l'accroche devient un
        // extrait d'un token, repris mot pour mot en tête du suivant.
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
        // La propriété la plus facile à casser sans s'en apercevoir : découper, ce n'est pas
        // jeter. Le recouvrement autorise les répétitions, jamais les absences.
        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(paragraphe(1, 200)));

        for (int numero = 1; numero <= 200; numero++) {
            String attendue = phrase(numero);
            assertThat(extraits)
                    .anySatisfy(extrait -> assertThat(extrait.text()).contains(attendue));
        }
    }

    /** Une phrase de dix mots, numérotée : dix tokens pour le compteur d'essai. */
    private static String phrase(int numero) {
        return "Phrase numero " + numero + " avec quelques mots pour occuper la place.";
    }

    /** Un paragraphe de {@code nombre} phrases, donc de dix fois autant de tokens. */
    private static String paragraphe(int premiere, int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(index -> phrase(premiere + index))
                .collect(Collectors.joining(" "));
    }

    /** Le même paragraphe, mais dont chaque phrase porte une marque reconnaissable. */
    private static String marque(String marque, int nombre) {
        return IntStream.range(0, nombre)
                .mapToObj(index -> marque + " numero " + index + " avec quelques mots pour occuper la place.")
                .collect(Collectors.joining(" "));
    }
}
