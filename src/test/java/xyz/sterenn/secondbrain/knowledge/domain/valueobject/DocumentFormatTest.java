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
    void reconnait_les_formats_acceptes(String nomDeFichier, DocumentFormat attendu) {
        assertThat(DocumentFormat.fromFilename(nomDeFichier)).isEqualTo(attendu);
    }

    @Test
    void ignore_la_casse_de_l_extension() {
        assertThat(DocumentFormat.fromFilename("RAPPORT.PDF")).isEqualTo(DocumentFormat.PDF);
    }

    @Test
    void reconnait_un_nom_comportant_plusieurs_points() {
        assertThat(DocumentFormat.fromFilename("compte-rendu.v2.final.docx")).isEqualTo(DocumentFormat.DOCX);
    }

    @Test
    void refuse_un_executable_en_enoncant_les_formats_acceptes() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename("virus.exe"))
                .isInstanceOf(UnsupportedDocumentFormatException.class)
                .hasMessageContaining(".pdf")
                .hasMessageContaining(".md")
                .hasMessageContaining(".txt")
                .hasMessageContaining(".docx");
    }

    @Test
    void refuse_un_nom_sans_extension() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename("LISEZMOI"))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    @Test
    void refuse_un_nom_absent() {
        assertThatThrownBy(() -> DocumentFormat.fromFilename(null))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    @Test
    void refuse_une_extension_qui_n_en_est_qu_un_fragment() {
        // « .pdfx » n'est pas « .pdf » : la comparaison porte sur la fin du nom entier.
        assertThatThrownBy(() -> DocumentFormat.fromFilename("rapport.pdfx"))
                .isInstanceOf(UnsupportedDocumentFormatException.class);
    }

    @Test
    void chaque_format_annonce_sa_typologie() {
        assertThat(DocumentFormat.values())
                .allSatisfy(format -> assertThat(format.type()).isNotNull());
    }

    @Test
    void les_quatre_formats_acceptes_se_decoupent_tous_en_texte() {
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL))
                .containsExactly(DocumentFormat.PDF, DocumentFormat.MARKDOWN, DocumentFormat.TEXT, DocumentFormat.DOCX);
    }
}
