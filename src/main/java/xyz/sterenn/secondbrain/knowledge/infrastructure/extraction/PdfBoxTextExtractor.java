package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/** Le sommaire d'abord, la taille de police en repli : voir ADR-0027. */
@Component
public class PdfBoxTextExtractor implements DocumentTextExtractor {

    @Override
    public DocumentFormat format() {
        return DocumentFormat.PDF;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        List<Section> sections;
        try (PDDocument pdf = Loader.loadPDF(content)) {
            PDDocumentOutline sommaire = pdf.getDocumentCatalog().getDocumentOutline();
            List<Bookmark> signets = sommaire == null ? List.of() : signets(pdf, sommaire, 1);
            sections = signets.isEmpty()
                    ? HeadingHeuristic.decouper(new HeadingFontStripper().lines(pdf))
                    : parSignets(pdf, signets);
        } catch (IOException illisible) {
            throw new UnreadableDocumentException(illisible);
        }
        // Hors du try : un PDF numérisé s'ouvre parfaitement, son refus est métier.
        return Section.assemble(sections);
    }

    private record Bookmark(String title, int level, int pageIndex) {}

    private static List<Bookmark> signets(PDDocument pdf, PDOutlineNode noeud, int niveau) throws IOException {
        List<Bookmark> trouves = new ArrayList<>();
        for (PDOutlineItem item : noeud.children()) {
            PDPage page = item.findDestinationPage(pdf);
            if (page != null && item.getTitle() != null && !item.getTitle().isBlank()) {
                trouves.add(new Bookmark(
                        item.getTitle(),
                        Math.min(niveau, TextBlock.MAX_HEADING_LEVEL),
                        pdf.getPages().indexOf(page)));
            }
            trouves.addAll(signets(pdf, item, niveau + 1));
        }
        return trouves;
    }

    private static List<Section> parSignets(PDDocument pdf, List<Bookmark> signets) throws IOException {
        // PDFBox ne découpe qu'en plages de pages : deux signets sur la même page sont
        // fusionnés sous le titre du premier, sinon cette page serait rendue deux fois.
        List<Bookmark> parPage = new ArrayList<>();
        for (Bookmark signet : signets) {
            if (parPage.isEmpty() || parPage.getLast().pageIndex() != signet.pageIndex()) {
                parPage.add(signet);
            }
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");

        List<Section> sections = new ArrayList<>();
        if (parPage.getFirst().pageIndex() > 0) {
            sections.add(
                    Section.untitled(texte(stripper, pdf, 0, parPage.getFirst().pageIndex() - 1)));
        }
        for (int i = 0; i < parPage.size(); i++) {
            Bookmark signet = parPage.get(i);
            int dernierePage = i + 1 < parPage.size() ? parPage.get(i + 1).pageIndex() - 1 : pdf.getNumberOfPages() - 1;
            sections.add(new Section(
                    signet.title(), signet.level(), texte(stripper, pdf, signet.pageIndex(), dernierePage)));
        }
        return sections;
    }

    /** Bornes inclusives en index de page ; {@link PDFTextStripper} les compte à partir de 1. */
    private static String texte(PDFTextStripper stripper, PDDocument pdf, int premierePage, int dernierePage)
            throws IOException {
        stripper.setStartPage(premierePage + 1);
        stripper.setEndPage(dernierePage + 1);
        return stripper.getText(pdf);
    }
}
