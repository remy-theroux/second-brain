package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Un {@link PDFTextStripper} qui, en plus du texte, retient la taille de police de chaque
 * ligne.
 *
 * <p>C'est la seule façon d'obtenir cette information : {@code getText} rend une chaîne, et
 * une chaîne ne dit rien de la police. {@code writeString} est le point de passage de chaque
 * ligne, avec les positions de ses glyphes.
 *
 * <p>{@code setSortByPosition(true)} parce que l'ordre du flux de contenu d'un PDF n'est pas
 * l'ordre de lecture, et {@code setLineSeparator("\n")} pour que le résultat ne dépende pas
 * du système d'exploitation qui fait tourner la suite.
 */
class HeadingFontStripper extends PDFTextStripper {

    private final List<TextLine> lines = new ArrayList<>();

    HeadingFontStripper() throws IOException {
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
     * Parcourt le document et rend ses lignes mesurées.
     *
     * <p>Le texte rendu par {@code getText} est jeté : ce qui nous intéresse a été collecté
     * en chemin. Un PDF numérisé n'appelle jamais {@code writeString} — la liste reste vide,
     * et c'est ainsi que le troisième scénario du ticket se solde par un refus.
     */
    List<TextLine> lines(PDDocument pdf) throws IOException {
        getText(pdf);
        return List.copyOf(lines);
    }
}
