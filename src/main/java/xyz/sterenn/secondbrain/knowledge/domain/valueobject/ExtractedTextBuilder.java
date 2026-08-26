package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.ArrayList;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

/**
 * Assemble un {@link ExtractedText} section par section. C'est le chemin normal d'un
 * extracteur : il parcourt un document, annonce chaque section telle qu'il la lit, et laisse
 * ici la question de savoir ce qui mérite un bloc.
 *
 * <p><strong>Une section dont le corps est vide est écartée sans bruit.</strong> Un
 * extracteur rencontre en permanence des titres suivis d'un autre titre, des paragraphes de
 * mise en page, des pages blanches : lui faire porter ce filtrage le rendrait bavard, et
 * chacun le rendrait à sa façon.
 *
 * <p>Conséquence à connaître : <strong>un titre immédiatement suivi d'un autre titre est
 * perdu.</strong> {@code # A} puis {@code ## B} ne rend que « B ». Le remède serait un chemin
 * de section, que {@code headingLevel} permet de reconstruire plus tard sans réextraire ;
 * l'inventer ici reviendrait à figer une convention d'affichage dans le domaine.
 *
 * <p>Mutable, et c'est assumé : c'est un échafaudage, pas une valeur. Ce qui sort de
 * {@link #build()} est immuable.
 */
public final class ExtractedTextBuilder {

    private final List<TextBlock> blocks = new ArrayList<>();

    /** Une section titrée. Sans corps, elle n'entre pas. */
    public ExtractedTextBuilder section(String heading, int headingLevel, String text) {
        if (!TextBlock.normalise(text).isEmpty()) {
            blocks.add(TextBlock.of(heading, headingLevel, text));
        }
        return this;
    }

    /** Ce qui n'appartient à aucune section : avant le premier titre, ou dans un document qui n'en a pas. */
    public ExtractedTextBuilder untitled(String text) {
        return section("", 0, text);
    }

    /**
     * @throws UnextractableDocumentException si rien n'a été retenu, ou si le total reste
     *     sous le plancher — c'est le refus exigé par le troisième scénario du ticket, et il
     *     tombe ici pour tous les extracteurs à la fois
     */
    public ExtractedText build() {
        return new ExtractedText(blocks);
    }
}
