package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentFormat;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentType;

public record DocumentDetailView(
        UUID id,
        String filename,
        DocumentFormat format,
        DocumentType type,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        long sizeBytes,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) TextExtractionView extraction) {

    public static DocumentDetailView of(Document document, TextExtractionView extraction) {
        return new DocumentDetailView(
                document.getId(),
                document.getFilename(),
                document.getFormat(),
                document.getFormat().type(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getSizeBytes(),
                document.getCreatedAt(),
                extraction);
    }
}
