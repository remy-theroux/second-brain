package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnsupportedDocumentFormatException;

public enum DocumentFormat {
    PDF(".pdf", DocumentType.TEXTUAL),
    MARKDOWN(".md", DocumentType.TEXTUAL),
    TEXT(".txt", DocumentType.TEXTUAL),
    DOCX(".docx", DocumentType.TEXTUAL);

    private final String extension;
    private final DocumentType type;

    DocumentFormat(String extension, DocumentType type) {
        this.extension = extension;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    public DocumentType type() {
        return type;
    }

    public static List<DocumentFormat> of(DocumentType type) {
        return Arrays.stream(values()).filter(format -> format.type == type).toList();
    }

    public static DocumentFormat fromFilename(String filename) {
        String normalise = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> normalise.endsWith(format.extension))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentFormatException(acceptedExtensions()));
    }

    public static String acceptedExtensions() {
        return Arrays.stream(values()).map(DocumentFormat::extension).collect(Collectors.joining(", "));
    }
}
