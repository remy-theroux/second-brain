package xyz.sterenn.secondbrain.knowledge.domain.port;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

/** Outbound port to reading one file format, one adapter per format — ADR-0026. */
public interface DocumentTextExtractor {

    DocumentFormat format();

    ExtractedText extract(byte[] content);
}
