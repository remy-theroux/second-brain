package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

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
        return trimmed.toLowerCase(Locale.ROOT).endsWith(extension) ? trimmed : trimmed + extension;
    }

    private DocumentFormat format() {
        return exportedFormat()
                .orElseThrow(() ->
                        new IllegalStateException("This type of Google Workspace file does not export: " + name()));
    }
}
