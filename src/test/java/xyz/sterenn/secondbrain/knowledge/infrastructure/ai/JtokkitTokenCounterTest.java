package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

class JtokkitTokenCounterTest {

    private final TokenCounter tokenCounter = new JtokkitTokenCounter();

    @Test
    void ne_compte_rien_dans_un_texte_absent_ou_vide() {
        assertThat(tokenCounter.count(null)).isZero();
        assertThat(tokenCounter.count("")).isZero();
    }

    @Test
    void compte_au_moins_un_token_par_mot() {
        assertThat(tokenCounter.count("Bonjour")).isPositive();
    }

    @Test
    void compte_plus_de_tokens_que_de_mots_sur_du_francais_accentue() {
        String texte = "L'élève déchiffrait péniblement les hiéroglyphes gravés sur la stèle funéraire. ".repeat(20);
        int mots = texte.strip().split("\\s+").length;

        assertThat(tokenCounter.count(texte)).isGreaterThan(mots);
    }

    @Test
    void compte_davantage_un_texte_plus_long() {
        String phrase = "Le chat dort sur le tapis.";

        assertThat(tokenCounter.count(phrase.repeat(3))).isGreaterThan(tokenCounter.count(phrase));
    }
}
