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
 * Run by hand — {@code gtest generateFixtures} — and its output is versioned: rebuilding it on
 * every build would mean a diff on every build. The text of the PDF fixtures carries no accents,
 * so that diagnosing a failing test stays readable.
 */
public final class FixtureFactory {

    private static final Path FIXTURES = Path.of("src", "test", "resources", "fixtures");

    private static final PDFont BODY = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont HEADING = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private FixtureFactory() {}

    public static void main(String[] arguments) throws IOException {
        Files.createDirectories(FIXTURES);
        writeHeadingsDocx();
        writeBookmarkedPdf();
        writeBookmarklessPdf();
        writeScannedPdf();
        System.out.println("Fixtures écrites dans " + FIXTURES.toAbsolutePath());
    }

    private static void writeHeadingsDocx() throws IOException {
        try (XWPFDocument docx = new XWPFDocument();
                OutputStream output = Files.newOutputStream(FIXTURES.resolve("titres.docx"))) {
            heading(docx, "Heading1", "Rapport annuel");
            paragraph(
                    docx,
                    "Le corps de l'introduction, assez long pour compter dans le plancher"
                            + " de caractères que le domaine impose à tout document extrait.");
            heading(docx, "Heading2", "Première partie");
            paragraph(docx, "Le corps de la première partie, sur deux paragraphes.");
            paragraph(
                    docx,
                    "Le second paragraphe de la première partie, pour vérifier que la"
                            + " frontière entre paragraphes survit à l'extraction.");
            heading(docx, "Heading3", "Un détail de la première partie");
            paragraph(docx, "Un troisième niveau, pour que la borne haute ne soit pas théorique.");
            heading(docx, "Heading2", "Seconde partie");
            paragraph(docx, "Le corps de la seconde partie, qui clôt le document.");
            docx.write(output);
        }
    }

    /** The cover page carries no bookmark: it is what the untitled block has to cover. */
    private static void writeBookmarkedPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream output = Files.newOutputStream(FIXTURES.resolve("signets.pdf"))) {
            textPage(
                    pdf,
                    List.of(
                            "Page de garde du rapport, avant tout signet.",
                            "Elle n'appartient a personne : aucune section ne la couvre."));
            PDPage firstPart = textPage(
                    pdf,
                    List.of(
                            "Le corps de la premiere partie, assez long pour compter",
                            "dans le plancher de caracteres du domaine."));
            PDPage secondPart = textPage(
                    pdf,
                    List.of(
                            "Le corps de la seconde partie, qui clot le document",
                            "et vaut lui aussi plus de cinquante caracteres."));

            PDDocumentOutline outline = new PDDocumentOutline();
            pdf.getDocumentCatalog().setDocumentOutline(outline);
            outline.addLast(bookmark("Premiere partie", firstPart));
            outline.addLast(bookmark("Seconde partie", secondPart));

            pdf.save(output);
        }
    }

    private static void writeBookmarklessPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream output = Files.newOutputStream(FIXTURES.resolve("sans-signets.pdf"))) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                float y = 780f;
                y = line(content, HEADING, 18f, y, "Rapport annuel");
                y = line(content, BODY, 11f, y, "Le corps de l'introduction, ecrit dans la taille");
                y = line(content, BODY, 11f, y, "qui porte de tres loin le plus de caracteres.");
                y = line(content, HEADING, 14f, y, "Premiere partie");
                y = line(content, BODY, 11f, y, "Le corps de la premiere partie, dans la meme taille");
                y = line(content, BODY, 11f, y, "que tout le reste du corps du document.");
                y = line(content, HEADING, 14f, y, "Seconde partie");
                line(content, BODY, 11f, y, "Le corps de la seconde partie, qui clot le document.");
            }
            pdf.save(output);
        }
    }

    private static void writeScannedPdf() throws IOException {
        try (PDDocument pdf = new PDDocument();
                OutputStream output = Files.newOutputStream(FIXTURES.resolve("numerise.pdf"))) {
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            BufferedImage scan = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D brush = scan.createGraphics();
            brush.setColor(Color.WHITE);
            brush.fillRect(0, 0, 600, 800);
            brush.setColor(Color.DARK_GRAY);
            // Strokes, not glyphs: none of this is text as far as a PDF is concerned.
            for (int i = 0; i < 24; i++) {
                brush.fillRect(60, 60 + i * 28, 40 + (i * 37) % 420, 6);
            }
            brush.dispose();
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.drawImage(LosslessFactory.createFromImage(pdf, scan), 20, 20, 555, 780);
            }
            pdf.save(output);
        }
    }

    private static PDPage textPage(PDDocument pdf, List<String> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
            float y = 780f;
            for (String text : lines) {
                y = line(content, BODY, 11f, y, text);
            }
        }
        return page;
    }

    private static float line(PDPageContentStream content, PDFont font, float size, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(60f, y);
        content.showText(text);
        content.endText();
        return y - size * 1.8f;
    }

    private static PDOutlineItem bookmark(String title, PDPage page) {
        PDOutlineItem item = new PDOutlineItem();
        item.setTitle(title);
        item.setDestination(page);
        return item;
    }

    private static void heading(XWPFDocument docx, String style, String text) {
        XWPFParagraph paragraph = docx.createParagraph();
        paragraph.setStyle(style);
        paragraph.createRun().setText(text);
    }

    private static void paragraph(XWPFDocument docx, String text) {
        docx.createParagraph().createRun().setText(text);
    }
}
