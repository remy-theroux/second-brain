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

/** The outline first, font size as fallback: see ADR-0027. */
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
            PDDocumentOutline outline = pdf.getDocumentCatalog().getDocumentOutline();
            List<Bookmark> bookmarks = outline == null ? List.of() : bookmarks(pdf, outline, 1);
            sections = bookmarks.isEmpty()
                    ? HeadingHeuristic.split(new HeadingFontStripper().lines(pdf))
                    : fromBookmarks(pdf, bookmarks);
        } catch (IOException unreadable) {
            throw new UnreadableDocumentException(unreadable);
        }
        // Outside the try: a scanned PDF opens perfectly, its refusal is a business one.
        return Section.assemble(sections);
    }

    private record Bookmark(String title, int level, int pageIndex) {}

    private static List<Bookmark> bookmarks(PDDocument pdf, PDOutlineNode node, int level) throws IOException {
        List<Bookmark> found = new ArrayList<>();
        for (PDOutlineItem item : node.children()) {
            PDPage page = item.findDestinationPage(pdf);
            if (page != null && item.getTitle() != null && !item.getTitle().isBlank()) {
                found.add(new Bookmark(
                        item.getTitle(),
                        Math.min(level, TextBlock.MAX_HEADING_LEVEL),
                        pdf.getPages().indexOf(page)));
            }
            found.addAll(bookmarks(pdf, item, level + 1));
        }
        return found;
    }

    private static List<Section> fromBookmarks(PDDocument pdf, List<Bookmark> bookmarks) throws IOException {
        // PDFBox only splits by page ranges: two bookmarks on the same page are merged under
        // the title of the first, otherwise that page would be rendered twice.
        List<Bookmark> byPage = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            if (byPage.isEmpty() || byPage.getLast().pageIndex() != bookmark.pageIndex()) {
                byPage.add(bookmark);
            }
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setLineSeparator("\n");

        List<Section> sections = new ArrayList<>();
        if (byPage.getFirst().pageIndex() > 0) {
            sections.add(
                    Section.untitled(text(stripper, pdf, 0, byPage.getFirst().pageIndex() - 1)));
        }
        for (int i = 0; i < byPage.size(); i++) {
            Bookmark bookmark = byPage.get(i);
            int lastPage = i + 1 < byPage.size() ? byPage.get(i + 1).pageIndex() - 1 : pdf.getNumberOfPages() - 1;
            sections.add(new Section(
                    bookmark.title(), bookmark.level(), text(stripper, pdf, bookmark.pageIndex(), lastPage)));
        }
        return sections;
    }

    /** Inclusive page-index bounds; {@link PDFTextStripper} counts them from 1. */
    private static String text(PDFTextStripper stripper, PDDocument pdf, int firstPage, int lastPage)
            throws IOException {
        stripper.setStartPage(firstPage + 1);
        stripper.setEndPage(lastPage + 1);
        return stripper.getText(pdf);
    }
}
