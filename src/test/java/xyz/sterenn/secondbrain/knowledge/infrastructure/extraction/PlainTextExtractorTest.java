package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/** Aucun Spring : un extracteur est un adapter, mais il n'a besoin d'aucun contexte. */
class PlainTextExtractorTest {

    private final PlainTextExtractor extracteur = new PlainTextExtractor();

    @Test
    void sait_lire_le_format_texte() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.TEXT);
    }

    @Test
    void rend_un_unique_bloc_sans_titre() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("brut.txt"));

        assertThat(texte.blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEmpty();
            assertThat(bloc.getHeadingLevel()).isZero();
            assertThat(bloc.getText()).contains("Notes prises pendant la réunion");
        });
    }

    @Test
    void conserve_la_frontiere_entre_les_paragraphes() {
        assertThat(extracteur.extract(Fixtures.lire("brut.txt")).blocks())
                .first()
                .extracting(TextBlock::getText, org.assertj.core.api.InstanceOfAssertFactories.STRING)
                .contains("\n\n");
    }

    @Test
    void lit_un_fichier_encode_en_iso_8859_1_plutot_que_d_echouer() {
        byte[] latin1 = "Une réunion très intéressante, tenue à Bruxelles en février.".getBytes(ISO_8859_1);

        assertThat(extracteur.extract(latin1).blocks().getFirst().getText()).contains("très intéressante");
    }

    @Test
    void refuse_un_fichier_qui_ne_dit_rien() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("   \n\n  ".getBytes(UTF_8)));
    }
}
