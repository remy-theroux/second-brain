package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;

public record DocumentView(
        UUID id,
        String filename,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        Instant createdAt) {

    public static DocumentView of(Document document) {
        return new DocumentView(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getCreatedAt());
    }
}
