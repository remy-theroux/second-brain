package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.EmptyFileException;
import org.apache.poi.UnsupportedFileFormatException;
import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JRuntimeException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnreadableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le {@code .docx} : les titres y sont explicites, mais nommés par le producteur du fichier.
 *
 * <p>Un titre Word est un paragraphe dont le <em>style</em> s'appelle {@code Heading1} à
 * {@code Heading9}. Deux pièges : un paragraphe simplement mis en gras et en grand n'est pas
 * un titre — et il ne doit pas en devenir un —, et l'identifiant du style n'est pas toujours
 * l'anglais. D'où les deux essais de {@link #niveauDeTitre} : l'identifiant d'abord, puis le
 * nom déclaré du style, où un Word français écrit « Titre 1 ».
 *
 * <p>Les tableaux ne sont pas lus : {@code getParagraphs()} rend le corps du document, pas
 * les cellules. C'est le hors-périmètre du ticket, pas un oubli.
 */
@Component
public class PoiDocxTextExtractor implements DocumentTextExtractor {

    private static final Pattern HEADING_STYLE =
            Pattern.compile("^(?:heading|titre)\\s*([1-9])$", Pattern.CASE_INSENSITIVE);

    @Override
    public DocumentFormat format() {
        return DocumentFormat.DOCX;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        List<Section> sections;
        try (XWPFDocument docx = new XWPFDocument(new ByteArrayInputStream(content))) {
            sections = lis(docx);
        } catch (IOException
                | UnsupportedFileFormatException
                | EmptyFileException
                | OpenXML4JRuntimeException
                | POIXMLException illisible) {
            // POI signale un zip qui n'est pas un docx par une famille d'exceptions dont
            // certaines héritent d'IllegalArgumentException : les nommer une à une plutôt
            // que d'attraper RuntimeException, qui masquerait un vrai défaut d'ici.
            throw new UnreadableDocumentException(illisible);
        }
        // Hors du try : le refus d'un document muet est métier, pas une panne de lecture.
        return Section.assemble(sections);
    }

    private static List<Section> lis(XWPFDocument docx) {
        List<Section> sections = new ArrayList<>();
        String titre = "";
        int niveau = 0;
        StringBuilder corps = new StringBuilder();

        for (XWPFParagraph paragraphe : docx.getParagraphs()) {
            String texte = paragraphe.getText();
            if (texte.isBlank()) {
                continue;
            }
            OptionalInt niveauDuTitre = niveauDeTitre(docx, paragraphe);
            if (niveauDuTitre.isPresent()) {
                sections.add(new Section(titre, niveau, corps.toString()));
                titre = texte;
                niveau = Math.min(niveauDuTitre.getAsInt(), TextBlock.MAX_HEADING_LEVEL);
                corps.setLength(0);
            } else {
                // Un paragraphe Word est un paragraphe : la double ligne le dit à RAG-5.
                corps.append(texte).append("\n\n");
            }
        }
        sections.add(new Section(titre, niveau, corps.toString()));
        return sections;
    }

    /** L'identifiant du style d'abord, son nom déclaré ensuite. Vide si ce n'est pas un titre. */
    private static OptionalInt niveauDeTitre(XWPFDocument docx, XWPFParagraph paragraphe) {
        String identifiant = paragraphe.getStyleID();
        if (identifiant == null) {
            return OptionalInt.empty();
        }
        OptionalInt parIdentifiant = niveau(identifiant);
        if (parIdentifiant.isPresent()) {
            return parIdentifiant;
        }
        XWPFStyles styles = docx.getStyles();
        XWPFStyle style = styles == null ? null : styles.getStyle(identifiant);
        return style == null ? OptionalInt.empty() : niveau(style.getName());
    }

    private static OptionalInt niveau(String nomDeStyle) {
        if (nomDeStyle == null) {
            return OptionalInt.empty();
        }
        Matcher correspondance = HEADING_STYLE.matcher(nomDeStyle.strip());
        return correspondance.matches()
                ? OptionalInt.of(Integer.parseInt(correspondance.group(1)))
                : OptionalInt.empty();
    }
}
