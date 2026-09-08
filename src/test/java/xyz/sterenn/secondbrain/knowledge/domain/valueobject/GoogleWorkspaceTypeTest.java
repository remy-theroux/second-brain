package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GoogleWorkspaceTypeTest {

    @Test
    void exports_a_google_document_as_docx() {
        Optional<GoogleWorkspaceType> type = GoogleWorkspaceType.forMimeType("application/vnd.google-apps.document");

        assertThat(type).contains(GoogleWorkspaceType.DOCUMENT);
        assertThat(type.orElseThrow().exportedFormat()).contains(DocumentFormat.DOCX);
        assertThat(type.orElseThrow().exportMimeType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test
    void ignores_a_spreadsheet_a_presentation_and_a_drawing() {
        assertThat(GoogleWorkspaceType.forMimeType("application/vnd.google-apps.spreadsheet"))
                .isPresent()
                .get()
                .satisfies(type -> assertThat(type.exportedFormat()).isEmpty());
        assertThat(GoogleWorkspaceType.forMimeType("application/vnd.google-apps.presentation"))
                .isPresent()
                .get()
                .satisfies(type -> assertThat(type.exportedFormat()).isEmpty());
        assertThat(GoogleWorkspaceType.forMimeType("application/vnd.google-apps.drawing"))
                .isPresent()
                .get()
                .satisfies(type -> assertThat(type.exportedFormat()).isEmpty());
    }

    @Test
    void is_not_concerned_by_an_ordinary_binary() {
        assertThat(GoogleWorkspaceType.forMimeType("application/pdf")).isEmpty();
        assertThat(GoogleWorkspaceType.forMimeType(null)).isEmpty();
    }

    @Test
    void names_the_exported_file_after_the_document_that_carries_no_extension() {
        assertThat(GoogleWorkspaceType.DOCUMENT.exportedName("Notes de réunion"))
                .isEqualTo("Notes de réunion.docx");
        assertThat(DocumentFormat.fromFilename(GoogleWorkspaceType.DOCUMENT.exportedName("Notes de réunion")))
                .isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void does_not_double_an_extension_the_document_already_carries() {
        assertThat(GoogleWorkspaceType.DOCUMENT.exportedName("contrat.docx")).isEqualTo("contrat.docx");
        assertThat(GoogleWorkspaceType.DOCUMENT.exportedName("contrat.DOCX")).isEqualTo("contrat.DOCX");
    }
}
