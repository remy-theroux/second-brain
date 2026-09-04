package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * {@code writeString} est le seul point de passage qui voie les positions des glyphes : le
 * {@code getText} d'un {@link PDFTextStripper} rend une chaîne, qui ne dit rien de la police.
 */
class HeadingFontStripper extends PDFTextStripper {

    private final List<TextLine> lines = new ArrayList<>();

    HeadingFontStripper() throws IOException {
        // L'ordre du flux de contenu d'un PDF n'est pas l'ordre de lecture, et le séparateur
        // fixé rend le résultat indépendant du système.
        setSortByPosition(true);
        setLineSeparator("\n");
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
        float plusGrande = 0f;
        for (TextPosition position : textPositions) {
            plusGrande = Math.max(plusGrande, position.getFontSizeInPt());
        }
        lines.add(new TextLine(text, plusGrande));
        super.writeString(text, textPositions);
    }

    /**
     * {@code getText} n'est appelé que pour son effet de bord : c'est {@code writeString} qui
     * collecte. Un PDF numérisé ne l'appelle jamais, et la liste reste vide.
     */
    List<TextLine> lines(PDDocument pdf) throws IOException {
        getText(pdf);
        return List.copyOf(lines);
    }
}
