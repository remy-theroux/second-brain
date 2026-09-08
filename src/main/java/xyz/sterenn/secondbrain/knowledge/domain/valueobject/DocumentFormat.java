package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnsupportedDocumentFormatException;

public enum DocumentFormat {
    PDF(".pdf", "application/pdf", DocumentType.TEXTUAL),
    MARKDOWN(".md", "text/markdown", DocumentType.TEXTUAL),
    TEXT(".txt", "text/plain", DocumentType.TEXTUAL),
    DOCX(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", DocumentType.TEXTUAL);

    private final String extension;
    private final String mediaType;
    private final DocumentType type;

    DocumentFormat(String extension, String mediaType, DocumentType type) {
        this.extension = extension;
        this.mediaType = mediaType;
        this.type = type;
    }

    public String extension() {
        return extension;
    }

    public String mediaType() {
        return mediaType;
    }

    public DocumentType type() {
        return type;
    }

    public static List<DocumentFormat> of(DocumentType type) {
        return Arrays.stream(values()).filter(format -> format.type == type).toList();
    }

    public static DocumentFormat fromFilename(String filename) {
        String normalised = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(format -> normalised.endsWith(format.extension))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentFormatException(acceptedExtensions()));
    }

    public static String acceptedExtensions() {
        return Arrays.stream(values()).map(DocumentFormat::extension).collect(Collectors.joining(", "));
    }
}
