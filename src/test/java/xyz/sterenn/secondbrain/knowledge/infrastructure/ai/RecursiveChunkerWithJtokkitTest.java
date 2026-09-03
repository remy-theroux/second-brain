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

/**
 * Le découpage mesuré par la <strong>vraie</strong> toise, pour ne pas ne vérifier que la
 * doublure de {@code RecursiveChunkerTest}.
 *
 * <p>Il vit dans le package de l'adapter et non dans celui du domaine : {@code
 * JtokkitTokenCounter} est package-private, et le rendre public pour un test serait payer le
 * test au prix de la règle.
 */
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
        // Le cas où il ne reste aucune frontière du tout : un contenu encodé collé dans un
        // document. Le compteur d'essai de RecursiveChunkerTest ne peut pas l'atteindre — un
        // seul « mot » y vaut un seul token.
        String blob = "QWxvcnNRdWVMZURvY3VtZW50TmVQb3J0ZUF1Y3VuZUZyb250aWVyZQ".repeat(400);

        List<Chunk> extraits = chunker.chunk(ExtractedText.untitled(blob));

        assertThat(extraits).hasSizeGreaterThan(1);
        assertThat(extraits).allSatisfy(extrait -> assertThat(tokenCounter.count(extrait.text()))
                .isLessThanOrEqualTo(ChunkingPolicy.MAX_TOKENS));
    }

    @Test
    void la_coupe_au_caractere_ne_separe_jamais_une_paire_de_substituts() {
        // Un emoji est une paire de substituts UTF-16 (U+1F600 en fait deux `char`). Une coupe
        // malheureuse en plein milieu laisserait une moitié orpheline, qui n'est plus de
        // l'UTF-8 valide — c'est le garde de splitOnCharacters. Le témoin est le round-trip
        // UTF-8 : un substitut orphelin n'y survit pas, il devient U+FFFD.
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
