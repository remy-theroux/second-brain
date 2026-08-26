package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PdfBoxTextExtractorTest {

    private final PdfBoxTextExtractor extracteur = new PdfBoxTextExtractor();

    @Test
    void sait_lire_le_format_pdf() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.PDF);
    }

    @Test
    void decoupe_un_pdf_a_sommaire_en_une_section_par_signet() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("signets.pdf"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly("", "Premiere partie", "Seconde partie");
    }

    @Test
    void garde_hors_section_la_page_de_garde_qui_precede_le_premier_signet() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("signets.pdf"));

        assertThat(texte.blocks()).first().satisfies(bloc -> {
            assertThat(bloc.getHeadingLevel()).isZero();
            assertThat(bloc.getText()).contains("Page de garde");
        });
    }

    @Test
    void ne_rend_jamais_deux_fois_le_texte_d_une_meme_page() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("signets.pdf"));

        assertThat(texte.blocks().get(1).getText()).doesNotContain("Page de garde");
        assertThat(texte.blocks().get(2).getText()).doesNotContain("premiere partie");
    }

    @Test
    void devine_les_titres_d_un_pdf_sans_sommaire_a_la_taille_de_police() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("sans-signets.pdf"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly("Rapport annuel", "Premiere partie", "Seconde partie");
        assertThat(texte.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 2);
    }

    @Test
    void refuse_un_pdf_numerise_sans_couche_texte() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extracteur.extract(Fixtures.lire("numerise.pdf")))
                .withMessageContaining("pas de texte exploitable");
    }

    @Test
    void refuse_un_fichier_qui_n_est_pas_un_pdf() {
        assertThatExceptionOfType(UnreadableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("Ceci n'est pas un PDF.".getBytes(UTF_8)))
                .withMessageContaining("n'a pas pu être lu");
    }
}
