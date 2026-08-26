package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class PoiDocxTextExtractorTest {

    private static final String CORPS =
            "Un corps de section assez long pour franchir le plancher de cinquante caractères.";

    private final PoiDocxTextExtractor extracteur = new PoiDocxTextExtractor();

    @Test
    void sait_lire_le_format_docx() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void rattache_chaque_bloc_au_titre_de_sa_section() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("titres.docx"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly(
                        "Rapport annuel", "Première partie", "Un détail de la première partie", "Seconde partie");
        assertThat(texte.blocks()).extracting(TextBlock::getHeadingLevel).containsExactly(1, 2, 3, 2);
    }

    @Test
    void conserve_la_frontiere_entre_deux_paragraphes_d_une_meme_section() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("titres.docx"));

        assertThat(texte.blocks().get(1).getText()).contains("\n\n");
    }

    @Test
    void reconnait_un_style_de_titre_nomme_en_francais() throws IOException {
        // Un Word français donne parfois un identifiant de style opaque et ne nomme le style
        // que dans <w:name> : c'est le repli que ce test exerce.
        byte[] docx = unDocxAuStylePersonnalise("Style42", "Titre 1", "Chapitre premier", CORPS);

        assertThat(extracteur.extract(docx).blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEqualTo("Chapitre premier");
            assertThat(bloc.getHeadingLevel()).isEqualTo(1);
        });
    }

    @Test
    void refuse_un_fichier_qui_n_est_pas_un_docx() {
        assertThatExceptionOfType(UnreadableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("Ceci n'est pas un document Word.".getBytes(UTF_8)))
                .withMessageContaining("n'a pas pu être lu");
    }

    @Test
    void refuse_un_docx_qui_ne_dit_rien() throws IOException {
        try (XWPFDocument vide = new XWPFDocument();
                ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            vide.createParagraph().createRun().setText("   ");
            vide.write(sortie);

            assertThatExceptionOfType(UnextractableDocumentException.class)
                    .isThrownBy(() -> extracteur.extract(sortie.toByteArray()));
        }
    }

    private static byte[] unDocxAuStylePersonnalise(String identifiant, String nomDuStyle, String titre, String corps)
            throws IOException {
        try (XWPFDocument docx = new XWPFDocument();
                ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            XWPFStyles styles = docx.createStyles();
            CTStyle definition = CTStyle.Factory.newInstance();
            definition.setStyleId(identifiant);
            definition.addNewName().setVal(nomDuStyle);
            styles.addStyle(new XWPFStyle(definition));

            XWPFParagraph paragrapheDeTitre = docx.createParagraph();
            paragrapheDeTitre.setStyle(identifiant);
            paragrapheDeTitre.createRun().setText(titre);
            docx.createParagraph().createRun().setText(corps);

            docx.write(sortie);
            return sortie.toByteArray();
        }
    }
}
