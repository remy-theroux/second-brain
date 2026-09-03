package xyz.sterenn.secondbrain.knowledge.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import xyz.sterenn.secondbrain.knowledge.domain.port.TokenCounter;

/**
 * Le vrai tokenizer, sans Spring : la classe n'a aucune dépendance à injecter.
 *
 * <p>Les assertions sont des <strong>relations</strong>, jamais des nombres attendus. Un
 * comptage exact figerait la table BPE de jtokkit dans le test : la moindre montée de
 * version le ferait rougir sans qu'aucune règle du projet n'ait bougé. Ce qui compte ici est
 * la propriété sur laquelle le plafond s'appuie — {@code cl100k} sur-compte le français,
 * donc un extrait sous le plafond l'est aussi pour {@code bge-m3}.
 */
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
        // La propriété qui rend le plafond conservateur : les mots français accentués se
        // découpent en plusieurs tokens `cl100k`. Un extrait de 800 tokens comptés ici reste
        // très en deçà des 8192 que bge-m3 accepte.
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
