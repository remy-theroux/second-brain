package xyz.sterenn.secondbrain.knowledge.infrastructure.extraction;

import java.util.List;
import org.springframework.stereotype.Component;
import xyz.sterenn.secondbrain.knowledge.domain.port.DocumentTextExtractor;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

@Component
public class PlainTextExtractor implements DocumentTextExtractor {

    @Override
    public DocumentFormat format() {
        return DocumentFormat.TEXT;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        return Section.assemble(List.of(Section.untitled(TextDecoding.decode(content))));
    }
}
