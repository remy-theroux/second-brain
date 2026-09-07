package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Embeddable
public class CitedSource {

    public static final int MAX_FILENAME_LENGTH = 255;
    public static final int MAX_HEADING_LENGTH = 255;

    @Column(name = "source_number", nullable = false)
    private int number;

    /** No constraint: it serves diagnosis, and is allowed to designate nobody any more. */
    @Column(name = "document_id", nullable = false, columnDefinition = "uuid")
    private UUID documentId;

    @Column(nullable = false, length = MAX_FILENAME_LENGTH)
    private String filename;

    @Column(nullable = false, length = MAX_HEADING_LENGTH)
    private String heading;

    @Column(nullable = false, columnDefinition = "text")
    private String text;

    protected CitedSource() {}

    private CitedSource(int number, UUID documentId, String filename, String heading, String text) {
        this.number = number;
        this.documentId = documentId;
        this.filename = filename;
        this.heading = heading;
        this.text = text;
    }

    public static CitedSource of(Source source) {
        return new CitedSource(
                source.number(), source.documentId(), source.filename(), source.heading(), source.text());
    }

    public int getNumber() {
        return number;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getFilename() {
        return filename;
    }

    public String getHeading() {
        return heading;
    }

    public String getText() {
        return text;
    }
}
