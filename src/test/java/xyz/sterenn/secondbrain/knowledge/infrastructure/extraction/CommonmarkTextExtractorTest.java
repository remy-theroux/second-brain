package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.Fixtures;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

class CommonmarkTextExtractorTest {

    private final CommonmarkTextExtractor extracteur = new CommonmarkTextExtractor();

    @Test
    void sait_lire_le_format_markdown() {
        assertThat(extracteur.format()).isEqualTo(DocumentFormat.MARKDOWN);
    }

    @Test
    void rattache_chaque_bloc_au_titre_de_sa_section() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("structure.md"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .containsExactly(
                        "Journal de bord", "Première section", "Un détail de la première section", "Seconde section");
    }

    @Test
    void rend_le_niveau_de_chaque_titre() {
        assertThat(extracteur.extract(Fixtures.lire("structure.md")).blocks())
                .extracting(TextBlock::getHeadingLevel)
                .containsExactly(1, 2, 3, 2);
    }

    @Test
    void ne_prend_pas_pour_un_titre_le_diese_d_un_bloc_de_code() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("structure.md"));

        assertThat(texte.blocks())
                .extracting(TextBlock::getHeading)
                .doesNotContain("ceci est un commentaire shell, pas une section");
        assertThat(texte.blocks().getLast().getText()).contains("echo bonjour");
    }

    @Test
    void un_markdown_sans_titre_donne_un_unique_bloc() {
        ExtractedText texte = extracteur.extract(Fixtures.lire("sans-titres.md"));

        assertThat(texte.blocks()).singleElement().satisfies(bloc -> {
            assertThat(bloc.getHeading()).isEmpty();
            assertThat(bloc.getHeadingLevel()).isZero();
        });
    }

    @Test
    void rend_le_texte_et_non_le_balisage() {
        String extrait = extracteur
                .extract(Fixtures.lire("sans-titres.md"))
                .blocks()
                .getFirst()
                .getText();

        assertThat(extrait).contains("emphase").doesNotContain("*emphase*").doesNotContain("`");
    }

    @Test
    void refuse_un_markdown_qui_n_a_que_des_titres() {
        assertThatExceptionOfType(UnextractableDocumentException.class)
                .isThrownBy(() -> extracteur.extract("# Un titre\n\n## Un autre\n".getBytes(UTF_8)));
    }
}
