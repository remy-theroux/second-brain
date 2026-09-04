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

@Component
public class CommonmarkTextExtractor implements DocumentTextExtractor {

    private final Parser parser = Parser.builder().build();

    // SEPARATE_BLOCKS conserve la double ligne entre deux blocs, que le découpage cherche ;
    // le rendu texte, lui, laisse tomber le balisage.
    private final TextContentRenderer renderer = TextContentRenderer.builder()
            .lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS)
            .build();

    @Override
    public DocumentFormat format() {
        return DocumentFormat.MARKDOWN;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        // Aucun try : le parseur CommonMark n'échoue jamais, tout entrant est du Markdown.
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
        return Section.assemble(sections);
    }
}
