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
 * Table cells are not read: {@code getParagraphs()} returns only the body of the document.
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
            sections = read(docx);
        } catch (IOException
                | UnsupportedFileFormatException
                | EmptyFileException
                | OpenXML4JRuntimeException
                | POIXMLException unreadable) {
            // POI reports a zip that is not a docx through a family of exceptions, some of which
            // extend IllegalArgumentException: naming them one by one rather than catching
            // RuntimeException, which would hide a genuine defect from here.
            throw new UnreadableDocumentException(unreadable);
        }
        // Outside the try: refusing a mute document is a business decision, not a read failure.
        return Section.assemble(sections);
    }

    private static List<Section> read(XWPFDocument docx) {
        List<Section> sections = new ArrayList<>();
        String heading = "";
        int level = 0;
        StringBuilder body = new StringBuilder();

        for (XWPFParagraph paragraph : docx.getParagraphs()) {
            String text = paragraph.getText();
            if (text.isBlank()) {
                continue;
            }
            OptionalInt headingLevel = headingLevelOf(docx, paragraph);
            if (headingLevel.isPresent()) {
                sections.add(new Section(heading, level, body.toString()));
                heading = text;
                level = Math.min(headingLevel.getAsInt(), TextBlock.MAX_HEADING_LEVEL);
                body.setLength(0);
            } else {
                body.append(text).append("\n\n");
            }
        }
        sections.add(new Section(heading, level, body.toString()));
        return sections;
    }

    /** The style id, then its declared name: a French Word writes "Titre 1". */
    private static OptionalInt headingLevelOf(XWPFDocument docx, XWPFParagraph paragraph) {
        String styleId = paragraph.getStyleID();
        if (styleId == null) {
            return OptionalInt.empty();
        }
        OptionalInt byId = levelOf(styleId);
        if (byId.isPresent()) {
            return byId;
        }
        XWPFStyles styles = docx.getStyles();
        XWPFStyle style = styles == null ? null : styles.getStyle(styleId);
        return style == null ? OptionalInt.empty() : levelOf(style.getName());
    }

    private static OptionalInt levelOf(String styleName) {
        if (styleName == null) {
            return OptionalInt.empty();
        }
        Matcher matcher = HEADING_STYLE.matcher(styleName.strip());
        return matcher.matches() ? OptionalInt.of(Integer.parseInt(matcher.group(1))) : OptionalInt.empty();
    }
}
