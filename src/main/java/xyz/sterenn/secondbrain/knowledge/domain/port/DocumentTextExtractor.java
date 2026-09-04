package xyz.sterenn.secondbrain.knowledge.domain.port;

import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.ExtractedText;

/** Port sortant vers la lecture d'un format de fichier, un adapter par format — ADR-0026. */
public interface DocumentTextExtractor {

    DocumentFormat format();

    ExtractedText extract(byte[] content);
}
