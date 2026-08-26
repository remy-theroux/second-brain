package xyz.sterenn.secondbrain.knowledge.fixtures;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

/**
 * Fabrique les documents d'essai binaires de {@code src/test/resources/fixtures/}.
 *
 * <p><strong>Se lance à la main, une fois, et son produit est versionné</strong> :
 * {@code gtest generateFixtures}. Elle n'est ni un test ni une étape de build — un fichier
 * refabriqué à chaque exécution ferait un diff à chaque exécution, et la suite ne testerait
 * plus que sa propre sortie du jour.
 *
 * <p>Ce que ces fichiers <strong>ne sont pas</strong> : de vrais documents personnels. Le
 * ticket en demandait cinq à dix, en intégration continue ; le porteur a tranché pour un
 * socle fabriqué (spec, décision 9). Un PDF écrit par PDFBox est un PDF aimable, celui qu'un
 * scanner produit ne l'est pas. La vérification sur documents réels reste un geste manuel,
 * sur la pile {@code docker compose}, et ce qu'elle révélera sera un ticket.
 */
public final class FixtureFactory {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private FixtureFactory() {
        // point d'entrée, pas un objet
    }

    public static void main(String[] arguments) throws IOException {
        Files.createDirectories(FIXTURES);
        ecrisTitresDocx();
        System.out.println("Fixtures écrites dans " + FIXTURES.toAbsolutePath());
    }

    /** Un DOCX à trois niveaux de titres, portés par les styles standard {@code HeadingN}. */
    private static void ecrisTitresDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("titres.docx"))) {
            titre(docx, "Heading1", "Rapport annuel");
            paragraphe(
                    docx,
                    "Le corps de l'introduction, assez long pour compter dans le plancher"
                            + " de caractères que le domaine impose à tout document extrait.");
            titre(docx, "Heading2", "Première partie");
            paragraphe(docx, "Le corps de la première partie, sur deux paragraphes.");
            paragraphe(
                    docx,
                    "Le second paragraphe de la première partie, pour vérifier que la"
                            + " frontière entre paragraphes survit à l'extraction.");
            titre(docx, "Heading3", "Un détail de la première partie");
            paragraphe(docx, "Un troisième niveau, pour que la borne haute ne soit pas théorique.");
            titre(docx, "Heading2", "Seconde partie");
            paragraphe(docx, "Le corps de la seconde partie, qui clôt le document.");
            docx.write(sortie);
        }
    }

    private static void titre(XWPFDocument docx, String style, String texte) {
        XWPFParagraph paragraphe = docx.createParagraph();
        paragraphe.setStyle(style);
        paragraphe.createRun().setText(texte);
    }

    private static void paragraphe(XWPFDocument docx, String texte) {
        docx.createParagraph().createRun().setText(texte);
    }
}
