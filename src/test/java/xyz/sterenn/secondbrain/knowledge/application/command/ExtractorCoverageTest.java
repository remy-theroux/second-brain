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

/**
 * Le contrôle de couverture des extracteurs, exercé sans Spring.
 *
 * <p>Il vit dans {@link ExtractDocumentTextHandler} et s'exécute à la construction du bean :
 * en production, un trou fait échouer le démarrage. Ici, on l'appelle directement — c'est la
 * seule façon de vérifier ce qu'il refuse sans casser le contexte de toute la suite.
 */
class ExtractorCoverageTest {

    /** Extracteur d'essai : il ne sait rien lire, seul le format qu'il revendique compte. */
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
        // Aucun format non textuel n'existe encore : ce que ce test fige, c'est que le
        // contrôle interroge la typologie du format, et non la seule appartenance à
        // `DocumentFormat.values()`. Il deviendra un vrai cas de refus le jour où la
        // deuxième typologie arrivera ; en attendant il constate que les deux ensembles
        // coïncident, donc que la restriction ne retire rien aujourd'hui.
        assertThat(DocumentFormat.of(DocumentType.TEXTUAL)).containsExactlyElementsOf(List.of(DocumentFormat.values()));
    }
}
