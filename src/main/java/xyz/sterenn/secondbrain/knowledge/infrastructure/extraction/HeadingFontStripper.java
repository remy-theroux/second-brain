package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * {@code writeString} is the only hook that sees glyph positions: the {@code getText} of a
 * {@link PDFTextStripper} returns a string, which says nothing about the font.
 */
class HeadingFontStripper extends PDFTextStripper {

    private final List<TextLine> lines = new ArrayList<>();

    HeadingFontStripper() throws IOException {
        // The order of a PDF content stream is not reading order, and a fixed separator makes
        // the result independent of the platform.
        setSortByPosition(true);
        setLineSeparator("\n");
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        float largest = 0f;
        for (TextPosition position : textPositions) {
            largest = Math.max(largest, position.getFontSizeInPt());
        }
        lines.add(new TextLine(text, largest));
        super.writeString(text, textPositions);
    }

    /**
     * {@code getText} is called only for its side effect: {@code writeString} does the
     * collecting. A scanned PDF never calls it, and the list stays empty.
     */
    List<TextLine> lines(PDDocument pdf) throws IOException {
        getText(pdf);
        return List.copyOf(lines);
    }
}
