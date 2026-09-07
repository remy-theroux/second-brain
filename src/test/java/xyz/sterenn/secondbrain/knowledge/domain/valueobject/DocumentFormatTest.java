package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnsupportedDocumentFormatException;

class DocumentFormatTest {

    @ParameterizedTest
    @CsvSource({
        "rapport.pdf, PDF",
        "notes.md, MARKDOWN",
        "brouillon.txt, TEXT",
        "contrat.docx, DOCX",
    })
    void recognises_the_accepted_formats(String filename, DocumentFormat expected) {
        assertThat(DocumentFormat.fromFilename(filename)).isEqualTo(expected);
    }

    @Test
    void ignores_the_case_of_the_extension() {
        assertThat(DocumentFormat.fromFilename("RAPPORT.PDF")).isEqualTo(DocumentFormat.PDF);
    }

    @Test
    void recognises_a_name_carrying_several_dots() {
        assertThat(DocumentFormat.fromFilename("compte-rendu.v2.final.docx")).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void rejects_an_executable_while_listing_the_accepted_formats() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename("virus.exe"))
                .isInstanceOf(UnsupportedDocumentFormatException.class)
                .hasMessageContaining(".pdf")
                .hasMessageContaining(".md")
                .hasMessageContaining(".txt")
                .hasMessageContaining(".docx");
    }

    @Test
    void rejects_a_name_without_an_extension() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename("LISEZMOI"))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    @Test
    void rejects_a_missing_name() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename(null))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    @Test
    void rejects_an_extension_that_is_only_a_fragment_of_one() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename("rapport.pdfx"))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    @Test
    void every_format_announces_its_type() {
        assertThat(DocumentFormat.values())
                .allSatisfy(format -> assertThat(format.type()).isNotNull());
    }

    @Test
    void the_four_accepted_formats_all_split_as_text() {
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL))
                .containsExactly(DocumentFormat.PDF, DocumentFormat.MARKDOWN, DocumentFormat.TEXT, DocumentFormat.DOCX);
    }
}
