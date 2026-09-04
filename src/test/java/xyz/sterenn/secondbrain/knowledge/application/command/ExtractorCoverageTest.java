package xyz.sterenn.secondbrain.knowledge.application.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

class ExtractorCoverageTest {

    private record ExtracteurFactice(DocumentFormat format) implements DocumentTextExtractor {
        @Override
        public ExtractedText extract(byte[] content) {
            throw new UnsupportedOperationException("Cet extracteur n'est là que pour son format");
        }
    }

    private static List<DocumentTextExtractor> couvreLaTypologieTextuelle() {
        return DocumentFormat.of(DocumentType.TEXTUAL).stream()
                .map(format -> (DocumentTextExtractor) new ExtracteurFactice(format))
                .toList();
    }

    @Test
    void accepte_un_extracteur_par_format_textuel() {
        assertThat(ExtractDocumentTextHandler.indexeParFormat(couvreLaTypologieTextuelle()))
                .containsOnlyKeys(DocumentFormat.of(DocumentType.TEXTUAL).toArray(DocumentFormat[]::new));
    }

    @Test
    void refuse_un_format_textuel_sans_extracteur() {
        List<DocumentTextExtractor> incomplet = couvreLaTypologieTextuelle().stream()
                .filter(extracteur -> extracteur.format() != DocumentFormat.DOCX)
                .toList();

        assertThatIllegalStateException()
                .isThrownBy(() -> ExtractDocumentTextHandler.indexeParFormat(incomplet))
                .withMessageContaining("DOCX");
    }

    @Test
    void refuse_deux_extracteurs_pour_le_meme_format() {
        List<DocumentTextExtractor> doublon = new ArrayList<>(couvreLaTypologieTextuelle());
        doublon.add(new ExtracteurFactice(DocumentFormat.PDF));

        assertThatIllegalStateException()
                .isThrownBy(() -> ExtractDocumentTextHandler.indexeParFormat(doublon))
                .withMessageContaining("PDF");
    }

    @Test
    void n_exige_un_extracteur_que_des_formats_de_typologie_textuelle() {
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL)).containsExactlyElementsOf(List.of(DocumentFormat.values()));
    }
}
