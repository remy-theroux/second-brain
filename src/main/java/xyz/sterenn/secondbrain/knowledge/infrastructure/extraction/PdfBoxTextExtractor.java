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

/**
 * Le {@code .pdf} : le format le plus fragile, parce qu'il ne porte <em>aucune</em>
 * sémantique de titre. Il n'y a que des glyphes posés à des coordonnées.
 *
 * <p>Deux stratégies, dans cet ordre, et c'est ADR-0027 :
 *
 * <ol>
 *   <li><strong>Le sommaire</strong> quand le document en a un. Il est écrit par l'auteur,
 *       et vaut mieux que n'importe quelle mesure.
 *   <li><strong>La taille de police</strong> sinon, par {@link HeadingHeuristic}.
 * </ol>
 *
 * <p><strong>Limite du chemin par sommaire : la granularité est la page.</strong> PDFBox ne
 * découpe qu'en plages de pages ; deux signets tombant sur la même page sont fusionnés sous
 * le titre du premier, faute de quoi le texte de cette page serait rendu deux fois — et pour
 * un RAG, un texte dupliqué est bien pire qu'un titre manquant.
 */
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
        // Hors du try : un PDF numérisé s'ouvre parfaitement, il ne dit simplement rien.
        // C'est un refus métier, pas une panne de lecture.
        return Section.assemble(sections);
    }

    /** Un signet : son titre, sa profondeur dans le sommaire, et la page qu'il vise. */
    private record Bookmark(String title, int level, int pageIndex) {}

    /**
     * Parcours en profondeur du sommaire : son ordre est l'ordre de lecture du document.
     *
     * <p>Un signet sans titre ou dont la destination ne mène à aucune page est écarté : ces
     * deux cas existent dans la nature, et un titre vide ne rattacherait rien à rien.
     */
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
        // Deux signets sur la même page sont fusionnés sous le titre du premier : PDFBox ne
        // découpe qu'en pages, et le texte de cette page serait sinon rendu deux fois.
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
        // Ce qui précède le premier signet — page de garde, résumé — n'appartient à aucune
        // section, et le perdre serait perdre du texte que l'auteur a bien écrit.
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

    /**
     * Bornes en index de page, inclusives ; {@link PDFTextStripper} les compte à partir de 1.
     *
     * <p>Un sommaire mal formé, dont les signets ne sont pas dans l'ordre des pages, donne
     * une plage vide plutôt qu'une erreur : la section est alors écartée par
     * {@link Section#assemble}, comme toute section sans corps.
     */
    private static String texte(PDFTextStripper stripper, PDDocument pdf, int premierePage, int dernierePage)
            throws IOException {
        stripper.setStartPage(premierePage + 1);
        stripper.setEndPage(dernierePage + 1);
        return stripper.getText(pdf);
    }
}
