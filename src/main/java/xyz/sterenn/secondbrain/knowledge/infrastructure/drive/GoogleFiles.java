package xyz.sterenn.secondbrain.knowledge.infrastructure.drive;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.GoogleWorkspaceType;

/** The one reading of a file entry, shared by the folder walk and the change feed. */
final class GoogleFiles {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleFiles.class);

    private GoogleFiles() {}

    /** Every field of the response is nullable: one unreadable entry drops out rather than failing the walk. */
    static Optional<DriveFile> toFile(GoogleFileResponse entry) {
        if (isBlank(entry.id()) || isBlank(entry.name())) {
            LOG.warn("Google handed back a file without an identifier or a name: dropped");
            return Optional.empty();
        }
        Optional<GoogleWorkspaceType> workspaceType = GoogleWorkspaceType.forMimeType(entry.mimeType());
        if (workspaceType.isPresent()) {
            return toExportedFile(entry, workspaceType.get());
        }
        Optional<DocumentFormat> format = DocumentFormat.forFilename(entry.name());
        if (format.isEmpty()) {
            return Optional.empty();
        }
        return sizeOf(entry)
                .map(size -> DriveFile.downloaded(
                        entry.id(), entry.name(), format.get(), size, entry.webViewLink(), modifiedTimeOf(entry)));
    }

    /** A sheet, a slide deck or a drawing drops out like an image: each would want its own typology. */
    private static Optional<DriveFile> toExportedFile(GoogleFileResponse entry, GoogleWorkspaceType workspaceType) {
        if (workspaceType.exportedFormat().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(DriveFile.exported(
                workspaceType, entry.id(), entry.name(), entry.webViewLink(), modifiedTimeOf(entry)));
    }

    /** Drive hands the size back as a string, and hands none at all back for a native Google Doc. */
    private static Optional<Long> sizeOf(GoogleFileResponse entry) {
        if (isBlank(entry.size())) {
            LOG.warn("Google told no size for the file {}: dropped", entry.id());
            return Optional.empty();
        }
        try {
            long size = Long.parseLong(entry.size().trim());
            if (size <= 0) {
                LOG.warn("Google told an empty size for the file {}: dropped", entry.id());
                return Optional.empty();
            }
            return Optional.of(size);
        } catch (NumberFormatException unreadable) {
            LOG.warn("Google told an unreadable size for the file {}: dropped", entry.id());
            return Optional.empty();
        }
    }

    private static Instant modifiedTimeOf(GoogleFileResponse entry) {
        if (isBlank(entry.modifiedTime())) {
            return null;
        }
        try {
            return Instant.parse(entry.modifiedTime());
        } catch (DateTimeParseException unreadable) {
            LOG.warn("Google told an unreadable modification time for the file {}", entry.id());
            return null;
        }
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
