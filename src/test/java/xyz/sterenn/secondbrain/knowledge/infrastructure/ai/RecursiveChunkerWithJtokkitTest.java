package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.ChunkingPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.RecursiveChunker;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.Chunk;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

class RecursiveChunkerWithJtokkitTest {

    private final TokenCounter tokenCounter = new JtokkitTokenCounter();
    private final RecursiveChunker chunker = new RecursiveChunker(tokenCounter);

    @Test
    void aucun_extrait_de_texte_francais_ne_depasse_le_plafond() {
        String texte = IntStream.range(0, 400)
                .mapToObj(index ->
                        "L'élève déchiffrait péniblement les hiéroglyphes gravés sur la stèle numéro " + index + ".")
                .collect(Collectors.joining(" "));

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(texte));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> assertThat(tokenCounter.count(extrait.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void coupe_au_caractere_un_bloc_sans_espace_ni_ponctuation() {
        // Le compteur d'essai de RecursiveChunkerTest ne peut pas atteindre ce cas : un seul
        // « mot » y vaut un seul token.
        String blob = "QWxvcnNRdWVMZURvY3VtZW50TmVQb3J0ZUF1Y3VuZUZyb250aWVyZQ".repeat(400);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(blob));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> assertThat(tokenCounter.count(extrait.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void la_coupe_au_caractere_ne_separe_jamais_une_paire_de_substituts() {
        // Un emoji est une paire de substituts UTF-16 : coupée en son milieu, la moitié
        // orpheline n'est plus de l'UTF-8 valide, et le round-trip la rend en U+FFFD.
        String blob = "😀".repeat(2000);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(blob));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> {
            assertThat(tokenCounter.count(extrait.text())).isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS);
            assertThat(new String(extrait.text().getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
                    .isEqualTo(extrait.text());
        });
    }
}
