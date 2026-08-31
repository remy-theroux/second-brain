package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.LineBreakRendering;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.TextBlock;

/**
 * Le {@code .md} : le seul format dont les titres sont explicites et sans ambiguïté.
 *
 * <p>Par le parseur CommonMark et non par une expression régulière sur {@code ^#} : le
 * dièse d'un commentaire dans un bloc de code n'est pas un titre, et un titre souligné
 * (« setext », un texte suivi d'une ligne de {@code ===}) en est un. Une expression
 * régulière se tromperait dans les deux sens.
 *
 * <p>Seuls les nœuds de <strong>premier niveau</strong> sont parcourus : un titre à
 * l'intérieur d'une citation ou d'un élément de liste ne découpe pas le document. Il
 * ressort dans le texte de sa section, ce qui est sa place.
 *
 * <p>{@code SEPARATE_BLOCKS} conserve la double ligne entre deux blocs — c'est elle que le
 * découpage de RAG-5 cherchera —, tandis que le rendu texte laisse tomber le balisage :
 * l'emphase et les accents graves n'ont rien à faire dans un extrait envoyé à un modèle.
 */
@Component
public class CommonmarkTextExtractor implements DocumentTextExtractor {

    private final Parser parser = Parser.builder().build();

    private final TextContentRenderer renderer = TextContentRenderer.builder()
            .lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS)
            .build();

    @Override
    public DocumentFormat format() {
        return DocumentFormat.MARKDOWN;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        // Aucun try : CommonMark n'a pas de document invalide. Tout ce qu'on lui donne est
        // du Markdown, au pire du Markdown qui ne dit rien — et c'est `assemble` qui refuse.
        Node document = parser.parse(TextDecoding.decode(content));

        List<Section> sections = new ArrayList<>();
        String titre = "";
        int niveau = 0;
        StringBuilder corps = new StringBuilder();

        for (Node noeud = document.getFirstChild(); noeud != null; noeud = noeud.getNext()) {
            if (noeud instanceof Heading titreMarkdown) {
                sections.add(new Section(titre, niveau, corps.toString()));
                titre = renderer.render(titreMarkdown);
                niveau = Math.min(titreMarkdown.getLevel(), TextBlock.MAX_HEADING_LEVEL);
                corps.setLength(0);
            } else {
                corps.append(renderer.render(noeud)).append("\n\n");
            }
        }
        sections.add(new Section(titre, niveau, corps.toString()));

        // La toute première section est vide dès que le document commence par un titre :
        // `assemble` l'écarte, comme toute section sans corps.
        return Section.assemble(sections);
    }
}
