package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.List;
import java.util.Objects;
import xyz.sterenn.secondbrain.knowledge.domain.ExtractionPolicy;
import xyz.sterenn.secondbrain.knowledge.domain.exception.UnextractableDocumentException;

/**
 * <strong>Le format commun à tous les documents extraits</strong> : une suite ordonnée de
 * blocs de texte, chacun rattaché au titre de sa section. Voir ADR-0024.
 *
 * <p>C'est le contrat entre l'extraction et tout ce qui viendra après — RAG-5 le découpe,
 * RAG-6 l'enchaîne, RAG-7 le remplace. Quatre formats de fichier entrent, une seule forme
 * en sort.
 *
 * <p><strong>Il est impossible d'en construire un qui soit vide.</strong> Le constructeur
 * compact refuse la liste vide et le total sous le plancher d'{@link ExtractionPolicy}, en
 * levant {@link UnextractableDocumentException} — le refus exigé par le troisième scénario
 * du ticket est donc porté par le type, pas par un contrôle qu'un extracteur pourrait
 * oublier.
 *
 * <p>Seul le corps des blocs compte dans le plancher, jamais les titres : un document dont
 * il ne resterait que des titres n'a pas de contenu.
 */
public record ExtractedText(List<TextBlock> blocks) {

    public ExtractedText {
        Objects.requireNonNull(blocks, "Les blocs de texte sont obligatoires");
        blocks = List.copyOf(blocks);
        if (!ExtractionPolicy.isExploitable(characterCount(blocks))) {
            throw new UnextractableDocumentException();
        }
    }

    /**
     * Un document dépourvu de toute structure : un seul bloc, sans titre.
     *
     * <p>Réservé au texte dont on sait déjà qu'il n'est pas vide. Un extracteur qui assemble
     * un document passe par {@link ExtractedTextBuilder}, qui écarte les sections vides
     * avant de tenter le refus.
     */
    public static ExtractedText untitled(String text) {
        return new ExtractedText(List.of(TextBlock.untitled(text)));
    }

    public int characterCount() {
        return characterCount(blocks);
    }

    private static int characterCount(List<TextBlock> blocks) {
        return blocks.stream().mapToInt(bloc -> bloc.getText().length()).sum();
    }
}
