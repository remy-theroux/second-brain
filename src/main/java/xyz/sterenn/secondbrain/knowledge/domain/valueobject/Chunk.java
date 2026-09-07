package xyz.sterenn.secondbrain.knowledge.domain.valueobject;

import java.util.Objects;

public record Chunk(String heading, String text) {

    public Chunk {
        Objects.requireNonNull(heading, "A section heading is required, empty when there is none");
        Objects.requireNonNull(text, "The chunk text is required");
        heading = heading.strip();
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A chunk without text is not a chunk: it cannot be built");
        }
    }

    public String contextualised(String filename) {
        Objects.requireNonNull(filename, "The document filename is required");
        String prefix =
                heading.isEmpty() ? "Document: " + filename : "Document: " + filename + " — Section: " + heading;
        return prefix + "\n\n" + text;
    }
}
