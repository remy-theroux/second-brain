package xyz.sterenn.secondbrain.knowledge.fixtures;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
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
 *
 * <p>Le texte des fixtures PDF est volontairement sans accents : les polices Standard 14 les
 * acceptent, mais les diagnostics d'un test qui échoue sont plus lisibles sans elles — et ce
 * que ces fichiers vérifient est la structure, pas l'encodage, dont {@code brut.txt} se charge.
 */
public final class FixtureFactory {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private static final PDFont CORPS = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont TITRE = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private FixtureFactory() {
        // point d'entrée, pas un objet
    }

    public static void main(String[] arguments) throws IOException {
        Files.createDirectories(FIXTURES);
        ecrisTitresDocx();
        ecrisSignetsPdf();
        ecrisSansSignetsPdf();
        ecrisNumerisePdf();
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

    /**
     * Un PDF de trois pages avec un sommaire : un signet par page, plus une page de garde qui
     * n'en a pas — c'est elle que le bloc sans titre doit couvrir.
     */
    private static void ecrisSignetsPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("signets.pdf"))) {
            pageDeTexte(
                    pdf,
                    List.of(
                            "Page de garde du rapport, avant tout signet.",
                            "Elle n'appartient a personne : aucune section ne la couvre."));
            PDPage premiere = pageDeTexte(
                    pdf,
                    List.of(
                            "Le corps de la premiere partie, assez long pour compter",
                            "dans le plancher de caracteres du domaine."));
            PDPage seconde = pageDeTexte(
                    pdf,
                    List.of(
                            "Le corps de la seconde partie, qui clot le document",
                            "et vaut lui aussi plus de cinquante caracteres."));

            PDDocumentOutline sommaire = new PDDocumentOutline();
            pdf.getDocumentCatalog().setDocumentOutline(sommaire);
            sommaire.addLast(signet("Premiere partie", premiere));
            sommaire.addLast(signet("Seconde partie", seconde));

            pdf.save(sortie);
        }
    }

    /** Un PDF sans sommaire, dont les titres ne se distinguent que par la taille de police. */
    private static void ecrisSansSignetsPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("sans-signets.pdf"))) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (PDPageContentStream flux = new PDPageContentStream(pdf, page)) {
                float y = 780f;
                y = ligne(flux, TITRE, 18f, y, "Rapport annuel");
                y = ligne(flux, CORPS, 11f, y, "Le corps de l'introduction, ecrit dans la taille");
                y = ligne(flux, CORPS, 11f, y, "qui porte de tres loin le plus de caracteres.");
                y = ligne(flux, TITRE, 14f, y, "Premiere partie");
                y = ligne(flux, CORPS, 11f, y, "Le corps de la premiere partie, dans la meme taille");
                y = ligne(flux, CORPS, 11f, y, "que tout le reste du corps du document.");
                y = ligne(flux, TITRE, 14f, y, "Seconde partie");
                ligne(flux, CORPS, 11f, y, "Le corps de la seconde partie, qui clot le document.");
            }
            pdf.save(sortie);
        }
    }

    /**
     * Un PDF numerise : une image, aucune couche texte. C'est le troisieme scenario du
     * ticket — l'extraction doit echouer, pas rendre du vide.
     */
    private static void ecrisNumerisePdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream sortie = Files.newOutputStream(FIXTURES.resolve("numerise.pdf"))) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            BufferedImage scan = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D pinceau = scan.createGraphics();
            pinceau.setColor(Color.WHITE);
            pinceau.fillRect(0, 0, 600, 800);
            pinceau.setColor(Color.DARK_GRAY);
            // Des traits, pas des glyphes : rien de ceci n'est du texte pour un PDF.
            for (int i = 0; i < 24; i++) {
                pinceau.fillRect(60, 60 + i * 28, 40 + (i * 37) % 420, 6);
            }
            pinceau.dispose();
            try (PDPageContentStream flux = new PDPageContentStream(pdf, page)) {
                flux.drawImage(LosslessFactory.createFromImage(pdf, scan), 20, 20, 555, 780);
            }
            pdf.save(sortie);
        }
    }

    private static PDPage pageDeTexte(PDDocument pdf, List<String> lignes) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        try (PDPageContentStream flux = new PDPageContentStream(pdf, page)) {
            float y = 780f;
            for (String texte : lignes) {
                y = ligne(flux, CORPS, 11f, y, texte);
            }
        }
        return page;
    }

    /** Écrit une ligne et rend l'ordonnée de la suivante. */
    private static float ligne(PDPageContentStream flux, PDFont police, float taille, float y, String texte)
            throws IOException {
        flux.beginText();
        flux.setFont(police, taille);
        flux.newLineAtOffset(60f, y);
        flux.showText(texte);
        flux.endText();
        return y - taille * 1.8f;
    }

    private static PDOutlineItem signet(String titre, PDPage page) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(titre);
        item.setDestination(page);
        return item;
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
