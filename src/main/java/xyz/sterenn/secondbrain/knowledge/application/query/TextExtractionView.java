package xyz.sterenn.secondbrain.knowledge.application.query;

import java.time.Instant;
import java.util.List;
import xyz.sterenn.secondbrain.knowledge.domain.entity.TextExtraction;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Projection de lecture du texte extrait : la forme que l'écran de détail affiche.
 *
 * <p>Propre à la typologie textuelle — une autre typologie aura la sienne, portée par le même
 * champ {@code extraction} de {@link DocumentDetailView} (ADR-0030).
 *
 * <p>Ni l'identifiant de l'extraction ni celui du document : le premier n'est désigné par
 * personne, le second est déjà celui de la ressource demandée.
 *
 * <p>{@code characterCount} est calculé par le domaine et compte les <em>corps</em> seuls,
 * jamais les titres : c'est la mesure qu'{@code ExtractionPolicy} utilise pour décider qu'un
 * document est inexploitable (ADR-0025), et l'écran doit montrer la même.
 */
public record TextExtractionView(Instant extractedAt, int characterCount, List<TextBlockView> blocks) {

    /** Un bloc, c'est-à-dire une section : son titre, son niveau, son corps normalisé. */
    public record TextBlockView(String heading, int headingLevel, String text) {}

    public static TextExtractionView of(TextExtraction extraction) {
        return new TextExtractionView(
                extraction.getExtractedAt(),
                extraction.text().characterCount(),
                extraction.getBlocks().stream().map(TextExtractionView::blockOf).toList());
    }

    private static TextBlockView blockOf(TextBlock block) {
        return new TextBlockView(block.getHeading(), block.getHeadingLevel(), block.getText());
    }
}
