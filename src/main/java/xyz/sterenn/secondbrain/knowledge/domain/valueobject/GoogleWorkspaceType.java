package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;

/**
 * What Drive calls a Doc, a sheet, a slide deck: none of them holds bytes to download, and only
 * the first exports into a format this base can read. The others would each want their own
 * typology and their own tables — see ADR-0030 — so they are skipped in silence, like an image.
 */
public enum GoogleWorkspaceType {
    DOCUMENT("application/vnd.google-apps.document", DocumentFormat.DOCX),
    SPREADSHEET("application/vnd.google-apps.spreadsheet", null),
    PRESENTATION("application/vnd.google-apps.presentation", null),
    DRAWING("application/vnd.google-apps.drawing", null),
    FORM("application/vnd.google-apps.form", null);

    private final String mimeType;
    private final DocumentFormat exportedFormat;

    GoogleWorkspaceType(String mimeType, DocumentFormat exportedFormat) {
        this.mimeType = mimeType;
        this.exportedFormat = exportedFormat;
    }

    public static Optional<GoogleWorkspaceType> forMimeType(String mimeType) {
        return Arrays.stream(values())
                .filter(type -> type.mimeType.equals(mimeType))
                .findFirst();
    }

    public Optional<DocumentFormat> exportedFormat() {
        return Optional.ofNullable(exportedFormat);
    }

    /** What Google is asked to produce, and what the produced file then is: the same media type. */
    public String exportMimeType() {
        return format().mediaType();
    }

    /**
     * A Google Doc carries no extension in its Drive name, and the format of a document is read
     * from its name: « Compte rendu du 3 mars » would be refused as it stands.
     */
    public String exportedName(String driveName) {
        String extension = format().extension();
        String trimmed = driveName == null ? "" : driveName.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return trimmed;
        }
        // The extension is added last, so the column's own truncation would be what eats it: a
        // document downloaded under a name without extension is one Windows will not open.
        int maxBaseLength = Document.MAX_FILENAME_LENGTH - extension.length();
        return (trimmed.length() > maxBaseLength ? trimmed.substring(0, maxBaseLength) : trimmed) + extension;
    }

    private DocumentFormat format() {
        return exportedFormat()
                .orElseThrow(() ->
                        new IllegalStateException("This type of Google Workspace file does not export: " + name()));
    }
}
