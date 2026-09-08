package xyz.sterenn.secondbrain.knowledge.application.query;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import xyz.sterenn.secondbrain.knowledge.domain.entity.Document;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentSource;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DocumentStatus;
import xyz.sterenn.secondbrain.knowledge.domain.valueobject.DriveProvenance;

public record DocumentView(
        UUID id,
        String filename,
        DocumentStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL) String errorMessage,
        DocumentSource source,
        @JsonInclude(JsonInclude.Include.NON_NULL) String driveLink,
        Instant createdAt) {

    public static DocumentView of(Document document) {
        return new DocumentView(
                document.getId(),
                document.getFilename(),
                document.getStatus(),
                document.getErrorMessage(),
                document.getSource(),
                driveLinkOf(document),
                document.getCreatedAt());
    }

    /**
     * The Drive identifier stays out: nothing on screen does anything with it, and the link is
     * what opens the file. A Drive that handed back no link leaves the field out.
     */
    static String driveLinkOf(Document document) {
        return document.getDriveProvenance().map(DriveProvenance::webViewLink).orElse(null);
    }
}
