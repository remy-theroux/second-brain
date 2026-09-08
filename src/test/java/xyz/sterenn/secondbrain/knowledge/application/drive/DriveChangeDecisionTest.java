package xyz.sterenn.secondbrain.knowledge.application.drive;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChangeDecision.Import;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChangeDecision.Reingest;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChangeDecision.Remove;
import xyz.sterenn.secondbrain.knowledge.application.drive.DriveChangeDecision.Rename;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Checksum;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveChange;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveFile;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.GoogleWorkspaceType;

/**
 * No Spring, no Drive, no database: the truth table of the mirror is exactly the kind of thing
 * that breaks in silence, so it is read here on its own.
 */
class DriveChangeDecisionTest {

    private static final String FILE_ID = "f1";

    private static final UUID OWNER = UUID.randomUUID();

    private static final UUID NOTES = UUID.randomUUID();

    private static final String LINK = "https://drive.google.com/file/d/f1/view";

    private static final Instant IMPORTED_AT = Instant.parse("2026-09-08T10:15:30Z");

    private static final Instant EDITED_LATER = IMPORTED_AT.plus(Duration.ofHours(1));

    private static final Checksum CHECKSUM = Checksum.of("le rapport".getBytes(StandardCharsets.UTF_8));

    @Test
    void re_ingests_a_file_whose_content_changed() {
        DriveChange change = aChangeOn(aBinary("rapport.pdf", EDITED_LATER));

        DriveChangeDecision decision = DriveChangeDecision.decide(change, Optional.of(aDocument()), inNotes());

        assertThat(decision).isInstanceOfSatisfying(Reingest.class, reingest -> {
            assertThat(reingest.document().getDriveProvenance())
                    .get()
                    .extracting(DriveProvenance::fileId)
                    .isEqualTo(FILE_ID);
            assertThat(reingest.file().documentName()).isEqualTo("rapport.pdf");
        });
    }

    /** A time the base does not hold is a move too: read as an immobility, it would freeze the document. */
    @Test
    void re_ingests_a_file_the_base_holds_no_modification_time_for() {
        Document document = Document.importedFromDrive(
                OWNER,
                "rapport.pdf",
                DocumentFormat.PDF,
                CHECKSUM,
                1024,
                new DriveProvenance(FILE_ID, LINK, null),
                NOTES);

        DriveChangeDecision decision = DriveChangeDecision.decide(
                aChangeOn(aBinary("rapport.pdf", IMPORTED_AT)), Optional.of(document), inNotes());

        assertThat(decision).isInstanceOf(Reingest.class);
    }

    @Test
    void imports_a_file_that_appeared_in_a_watched_folder() {
        DriveChange change = aChangeOn(aBinary("nouveau.pdf", IMPORTED_AT));

        DriveChangeDecision decision = DriveChangeDecision.decide(change, Optional.empty(), inNotes());

        assertThat(decision).isInstanceOfSatisfying(Import.class, imported -> {
            assertThat(imported.file().id()).isEqualTo(FILE_ID);
            assertThat(imported.watchedFolderId()).isEqualTo(NOTES);
        });
    }

    /** The change carries no file at all, hence no parents: nothing may dereference one. */
    @Test
    void removes_a_file_that_left_the_drive() {
        Document document = aDocument();

        DriveChangeDecision decision =
                DriveChangeDecision.decide(DriveChange.removed(FILE_ID), Optional.of(document), Optional.empty());

        assertThat(decision).isInstanceOfSatisfying(Remove.class, remove -> assertThat(remove.document())
                .isSameAs(document));
    }

    @Test
    void removes_a_file_sent_to_the_trash() {
        Document document = aDocument();

        DriveChangeDecision decision =
                DriveChangeDecision.decide(DriveChange.trashed(FILE_ID), Optional.of(document), inNotes());

        assertThat(decision).isInstanceOfSatisfying(Remove.class, remove -> assertThat(remove.document())
                .isSameAs(document));
    }

    @Test
    void removes_a_file_moved_out_of_every_watched_folder() {
        Document document = aDocument();
        DriveChange change = new DriveChange(FILE_ID, false, false, aBinary("rapport.pdf", IMPORTED_AT), List.of("z9"));

        DriveChangeDecision decision = DriveChangeDecision.decide(change, Optional.of(document), Optional.empty());

        assertThat(decision).isInstanceOfSatisfying(Remove.class, remove -> assertThat(remove.document())
                .isSameAs(document));
    }

    @Test
    void renames_a_file_whose_name_changed_and_nothing_else() {
        Document document = aDocument();

        DriveChangeDecision decision = DriveChangeDecision.decide(
                aChangeOn(aBinary("rapport-2026.pdf", IMPORTED_AT)), Optional.of(document), inNotes());

        assertThat(decision).isInstanceOfSatisfying(Rename.class, rename -> {
            assertThat(rename.document()).isSameAs(document);
            assertThat(rename.filename()).isEqualTo("rapport-2026.pdf");
        });
    }

    /** One command, never two: {@code ReplaceDocumentContent} already carries the name. */
    @Test
    void renames_and_re_ingests_a_file_whose_name_and_content_both_changed() {
        DriveChangeDecision decision = DriveChangeDecision.decide(
                aChangeOn(aBinary("rapport-2026.pdf", EDITED_LATER)), Optional.of(aDocument()), inNotes());

        assertThat(decision).isInstanceOfSatisfying(Reingest.class, reingest -> assertThat(
                        reingest.file().documentName())
                .isEqualTo("rapport-2026.pdf"));
    }

    @Test
    void does_nothing_for_a_file_moved_inside_the_same_watched_folder() {
        DriveChange change =
                new DriveChange(FILE_ID, false, false, aBinary("rapport.pdf", IMPORTED_AT), List.of("sous-dossier"));

        DriveChangeDecision decision = DriveChangeDecision.decide(change, Optional.of(aDocument()), inNotes());

        assertThat(decision).isEqualTo(DriveChangeDecision.NOTHING);
    }

    @Test
    void does_nothing_when_nothing_moved() {
        DriveChangeDecision decision = DriveChangeDecision.decide(
                aChangeOn(aBinary("rapport.pdf", IMPORTED_AT)), Optional.of(aDocument()), inNotes());

        assertThat(decision).isEqualTo(DriveChangeDecision.NOTHING);
    }

    @Test
    void does_nothing_for_a_change_on_a_file_the_base_never_held() {
        DriveChange change =
                new DriveChange(FILE_ID, false, false, aBinary("ailleurs.pdf", IMPORTED_AT), List.of("z9"));

        assertThat(DriveChangeDecision.decide(change, Optional.empty(), Optional.empty()))
                .isEqualTo(DriveChangeDecision.NOTHING);
    }

    @Test
    void does_nothing_for_a_file_the_base_never_held_that_left_the_drive() {
        assertThat(DriveChangeDecision.decide(DriveChange.removed(FILE_ID), Optional.empty(), Optional.empty()))
                .isEqualTo(DriveChangeDecision.NOTHING);
    }

    /** An image, a sheet, a slide deck: the walk drops them, and a change on one is no gesture. */
    @Test
    void does_nothing_for_a_change_on_a_file_it_could_never_open() {
        DriveChange change = DriveChange.of(FILE_ID, null, List.of("a1"));

        assertThat(DriveChangeDecision.decide(change, Optional.empty(), inNotes()))
                .isEqualTo(DriveChangeDecision.NOTHING);
    }

    /** Nothing readable to judge on is not a reason to erase: an incomplete read removes nothing. */
    @Test
    void keeps_a_document_whose_change_carries_nothing_readable() {
        assertThat(DriveChangeDecision.decide(
                        DriveChange.of(FILE_ID, null, List.of("a1")), Optional.of(aDocument()), inNotes()))
                .isEqualTo(DriveChangeDecision.NOTHING);
    }

    /**
     * The costly invariant: an export is rebuilt on every call, so a Doc whose modification time
     * has not moved must not even be exported — paying the transfer to discard it every run.
     */
    @Test
    void does_not_re_export_a_google_document_whose_modified_time_has_not_changed() {
        Document document = Document.importedFromDrive(
                OWNER,
                "Compte rendu.docx",
                DocumentFormat.DOCX,
                CHECKSUM,
                1024,
                new DriveProvenance(FILE_ID, LINK, IMPORTED_AT),
                NOTES);

        DriveChangeDecision decision = DriveChangeDecision.decide(
                aChangeOn(aGoogleDoc("Compte rendu", IMPORTED_AT)), Optional.of(document), inNotes());

        assertThat(decision).isEqualTo(DriveChangeDecision.NOTHING);
    }

    @Test
    void renames_a_google_document_without_exporting_it_again() {
        Document document = Document.importedFromDrive(
                OWNER,
                "Compte rendu.docx",
                DocumentFormat.DOCX,
                CHECKSUM,
                1024,
                new DriveProvenance(FILE_ID, LINK, IMPORTED_AT),
                NOTES);

        DriveChangeDecision decision = DriveChangeDecision.decide(
                aChangeOn(aGoogleDoc("Compte rendu du 3 mars", IMPORTED_AT)), Optional.of(document), inNotes());

        assertThat(decision).isInstanceOfSatisfying(Rename.class, rename -> assertThat(rename.filename())
                .isEqualTo("Compte rendu du 3 mars.docx"));
    }

    private static Document aDocument() {
        return Document.importedFromDrive(
                OWNER,
                "rapport.pdf",
                DocumentFormat.PDF,
                CHECKSUM,
                1024,
                new DriveProvenance(FILE_ID, LINK, IMPORTED_AT),
                NOTES);
    }

    private static DriveFile aBinary(String name, Instant modifiedTime) {
        return DriveFile.downloaded(FILE_ID, name, DocumentFormat.PDF, 1024, LINK, modifiedTime);
    }

    private static DriveFile aGoogleDoc(String name, Instant modifiedTime) {
        return DriveFile.exported(GoogleWorkspaceType.DOCUMENT, FILE_ID, name, LINK, modifiedTime);
    }

    private static DriveChange aChangeOn(DriveFile file) {
        return DriveChange.of(FILE_ID, file, List.of("a1"));
    }

    private static Optional<UUID> inNotes() {
        return Optional.of(NOTES);
    }
}
