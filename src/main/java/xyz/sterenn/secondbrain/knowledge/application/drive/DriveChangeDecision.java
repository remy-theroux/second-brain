package xyz.sterenn.secondbrain.knowledge.application.drive;

import java.util.Optional;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;

/**
 * The gesture one change of the Drive calls for. Pure logic, on purpose: separated from what
 * carries it out, the truth table of the mirror is read without Spring, without Drive and
 * without a database.
 */
public sealed interface DriveChangeDecision {

    DriveChangeDecision NOTHING = new Nothing();

    record Import(DriveFile file, UUID watchedFolderId) implements DriveChangeDecision {}

    record Reingest(Document document, DriveFile file) implements DriveChangeDecision {}

    record Rename(Document document, String filename) implements DriveChangeDecision {}

    record Remove(Document document) implements DriveChangeDecision {}

    record Nothing() implements DriveChangeDecision {}

    /**
     * @param known the document this owner holds for the changed file, empty when the base never
     *     held it
     * @param watchedFolder the watched folder the file now sits under, empty when it sits under
     *     none — which is a removal, so it must be told apart from a folder the caller failed to
     *     resolve: a failed synchronisation erases nothing
     */
    static DriveChangeDecision decide(DriveChange change, Optional<Document> known, Optional<UUID> watchedFolder) {
        if (known.isEmpty()) {
            return appeared(change, watchedFolder);
        }
        Document document = known.get();
        if (change.isGone() || watchedFolder.isEmpty()) {
            return new Remove(document);
        }
        // Nothing readable to judge on is not a reason to erase: a format this base could never
        // open, or an entry Drive described too poorly to read.
        return change.readableFile()
                .<DriveChangeDecision>map(file -> reflect(document, file))
                .orElse(NOTHING);
    }

    private static DriveChangeDecision appeared(DriveChange change, Optional<UUID> watchedFolder) {
        if (change.isGone() || watchedFolder.isEmpty()) {
            return NOTHING;
        }
        return change.readableFile()
                .<DriveChangeDecision>map(file -> new Import(file, watchedFolder.get()))
                .orElse(NOTHING);
    }

    /**
     * The modification time before the name, and one command rather than two: a content that
     * moved is re-ingested by {@code ReplaceDocumentContent}, which already carries the name.
     * Reversed, a file whose name and content both changed would only be renamed.
     */
    private static DriveChangeDecision reflect(Document document, DriveFile file) {
        if (document.movedInDriveSinceTheImport(file.modifiedTime())) {
            return new Reingest(document, file);
        }
        if (!file.documentName().equals(document.getFilename())) {
            return new Rename(document, file.documentName());
        }
        return NOTHING;
    }
}
