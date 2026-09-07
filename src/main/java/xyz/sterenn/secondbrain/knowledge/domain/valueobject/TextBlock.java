package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.text.Normalizer;
import java.util.Objects;

/** See ADR-0002: the deviation that allows JPA annotations in the domain. */
@Embeddable
public class TextBlock {

    public static final int MAX_HEADING_LENGTH = 255;

    public static final int MAX_HEADING_LEVEL = 6;

    @Column(nullable = false, length = MAX_HEADING_LENGTH)
    private String heading;

    @Column(name = "heading_level", nullable = false)
    private int headingLevel;

    // Explicit columnDefinition: without it, Hibernate would expect a varchar(255) and
    // `ddl-auto: validate` would refuse to start against a `text` column.
    @Column(nullable = false, columnDefinition = "text")
    private String text;

    protected TextBlock() {}

    private TextBlock(String heading, int headingLevel, String text) {
        this.heading = heading;
        this.headingLevel = headingLevel;
        this.text = text;
    }

    public static TextBlock of(String heading, int headingLevel, String text) {
        String normalisedHeading = normaliseHeading(heading);
        String body = normalise(text);
        if (body.isEmpty()) {
            throw new IllegalArgumentException("A block without text is not a block: it cannot be built");
        }
        if (!normalisedHeading.isEmpty() && (headingLevel < 1 || headingLevel > MAX_HEADING_LEVEL)) {
            throw new IllegalArgumentException(
                    "A heading level ranges from 1 to " + MAX_HEADING_LEVEL + ", got: " + headingLevel);
        }
        return new TextBlock(normalisedHeading, normalisedHeading.isEmpty() ? 0 : headingLevel, body);
    }

    public static TextBlock untitled(String text) {
        return of("", 0, text);
    }

    static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
                .replace('\uFEFF', ' ') // byte order mark, at the head of a UTF-8 file
                .replace('\u00A0', ' ') // non-breaking space: a space, not nothing — dropping it would glue words
                .replace("\u0000", "") // null byte: a malformed PDF sows them, and PostgreSQL rejects it
                .replace("\u00AD", "") // soft hyphen: it would cut words
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t\\x0B\\f]+(?=\\n)", "")
                .replaceAll("\n{3,}", "\n\n") // the paragraph boundary survives, the layout does not
                .strip();
    }

    private static String normaliseHeading(String raw) {
        String normalised = normalise(raw).replaceAll("\\s+", " ").strip();
        return normalised.length() > MAX_HEADING_LENGTH
                ? normalised.substring(0, MAX_HEADING_LENGTH).strip()
                : normalised;
    }

    public String getHeading() {
        return heading;
    }

    public int getHeadingLevel() {
        return headingLevel;
    }

    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TextBlock block
                && headingLevel == block.headingLevel
                && Objects.equals(heading, block.heading)
                && Objects.equals(text, block.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(heading, headingLevel, text);
    }

    @Override
    public String toString() {
        return "TextBlock[heading=" + heading + ", level=" + headingLevel + ", " + text.length() + " characters]";
    }
}
