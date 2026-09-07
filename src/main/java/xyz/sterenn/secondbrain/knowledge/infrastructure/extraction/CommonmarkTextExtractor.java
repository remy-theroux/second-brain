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

    // SEPARATE_BLOCKS keeps the blank line between two blocks, which the chunker looks for;
    // the text rendering itself drops the markup.
    private final TextContentRenderer renderer = TextContentRenderer.builder()
            .lineBreakRendering(LineBreakRendering.SEPARATE_BLOCKS)
            .build();

    @Override
    public DocumentFormat format() {
        return DocumentFormat.MARKDOWN;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        // No try: the CommonMark parser never fails, any input is Markdown.
        Node document = parser.parse(TextDecoding.decode(content));

        List<Section> sections = new ArrayList<>();
        String heading = "";
        int level = 0;
        StringBuilder body = new StringBuilder();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading markdownHeading) {
                sections.add(new Section(heading, level, body.toString()));
                heading = renderer.render(markdownHeading);
                level = Math.min(markdownHeading.getLevel(), TextBlock.MAX_HEADING_LEVEL);
                body.setLength(0);
            } else {
                body.append(renderer.render(node)).append("\n\n");
            }
        }
        sections.add(new Section(heading, level, body.toString()));
        return Section.assemble(sections);
    }
}
